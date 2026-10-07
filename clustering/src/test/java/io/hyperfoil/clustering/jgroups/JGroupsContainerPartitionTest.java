package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.Network.Ipam;

/**
 * Partitions a three node cluster by taking the network interface away from one container, then gives it back.
 * <p>
 * Every other partition test in this module induces the split <em>inside</em> the protocol stack, with
 * {@code DISCARD} dropping messages between protocols, in one JVM, on loopback. Here the fault is below the JVM
 * entirely - {@code docker network disconnect} removes the container's veth - and the nodes are three separate
 * JVMs in three separate network namespaces. What that buys over the in-stack tests:
 * <ul>
 * <li>This is the only test that forms a cluster on the <strong>shipped {@code jgroups-tcp.xml} itself</strong>,
 * byte for byte, rather than on a copy carrying test deltas ({@link JGroupsStackParseTest} reads the shipped
 * file but never connects). It needs no deltas: the file already takes its bind address, port and
 * {@code initial_hosts} from system properties, and giving every node its own container removes the reasons
 * {@code jgroups-tcp-test.xml} had to override them. Failure detection therefore runs at production timings,
 * which nothing else here does.</li>
 * <li>The sockets are real and so is the recovery. The split is a genuine split brain - the {@code MergeView}
 * that ends it reports two subgroups - and TCPPING has to rediscover across it before MERGE3 can stitch the
 * halves back together.</li>
 * <li>Registrations and node info written on <em>both</em> sides while the cluster is split have to survive
 * that merge. This is the repair path - {@code viewAccepted} announcing each node's own slice whenever the view
 * grows - running between processes that cannot share anything but the wire.</li>
 * </ul>
 * <p>
 * <strong>What it does not buy, which is worth stating because the obvious guess is wrong: FD_SOCK2 still does
 * not fire.</strong> Removing an interface closes no sockets. FD_SOCK2 is purely connection driven - its ring
 * connections carry no traffic, so there is nothing in flight to time out - and for the transport's own
 * connections an unreachable peer is a soft TCP error, retried for minutes rather than reported. The split is
 * observed at 45-50s, which is FD_ALL3's 40s timeout plus its 8s heartbeat granularity: at the TCP level
 * this fault is the same class as {@code DISCARD}, just with real sockets underneath it. Only a socket that
 * actually closes reaches FD_SOCK2 - a dead process, which {@code AgentCrashTest} covers and which never comes
 * back, or a proxy that resets the connection, which is the rung above this one in the PR's limitations.
 * <p>
 * Each container keeps two networks and this is load bearing. The control socket {@link ContainerNode} serves
 * lives on the default bridge, which is never touched, so the test can still talk to a node it has just cut off
 * - otherwise it could induce a partition but not observe one. JGroups traffic lives on a dedicated network with
 * fixed addresses, which is the one that gets disconnected. The addresses are fixed because TCPPING's
 * {@code initial_hosts} is static: a node that came back on a different address would be unreachable for a
 * reason that has nothing to do with what is under test.
 * <p>
 * Tagged {@code Container} rather than {@code Benchmark} like the clustered tests in {@code test-suite}, because
 * what it needs is a Docker daemon rather than time and load, but excluded from the default build by the same
 * {@code -P benchmark} switch. It skips rather than fails where no daemon is reachable. It takes around 70s,
 * nearly all of it the wait above; buying that back would mean tuning the one stack this test exists to
 * leave alone.
 */
@Tag("io.hyperfoil.test.Container")
public class JGroupsContainerPartitionTest {
   /** The base image {@code distribution/src/main/docker/Dockerfile} ships on - a stock JDK, nothing built. */
   private static final String IMAGE = "registry.access.redhat.com/ubi9/openjdk-21:1.23";
   private static final int JGROUPS_PORT = 7800;
   private static final int CONTROL_PORT = 9000;

   private static final long AWAIT_MS = 90_000;
   /**
    * Derived from the shipped FD_ALL3 defaults rather than picked: {@code timeout=40000} plus the
    * {@code interval=8000} granularity of the heartbeat that has to go missing first, then doubled, because a
    * loaded CI machine delays heartbeats as readily as a partition does. Measured at ~50s. Shortening this
    * would not make the test faster - it would only make it fail.
    */
   private static final long SPLIT_DEADLINE_MS = 96_000;

   private static final String PRE = "before-the-split";
   private static final String DURING = "during-the-split";

   private DockerClient docker;
   private String networkId;
   private String[] addresses;
   private final List<Node> nodes = new ArrayList<>();

   @BeforeEach
   public void before() {
      assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "no Docker daemon");
      docker = DockerClientFactory.instance().client();
      String clusterName = "container-partition-" + System.nanoTime();
      // The second octet is drawn per run rather than fixed. The daemon refuses a network whose subnet
      // overlaps an existing one, so a constant subnet means a second concurrent run cannot start - and,
      // worse, that a single leaked network fails every later run of this test on the machine with "Pool
      // overlaps with other one on this address space", which says nothing about the code under test.
      String subnet = "10." + (77 + ThreadLocalRandom.current().nextInt(100)) + ".0";
      addresses = new String[] { subnet + ".11", subnet + ".12", subnet + ".13" };
      networkId = docker.createNetworkCmd()
            .withName(clusterName)
            .withDriver("bridge")
            .withIpam(new Ipam().withConfig(new Ipam.Config().withSubnet(subnet + ".0/24")))
            .exec().getId();
      for (int i = 0; i < addresses.length; i++) {
         nodes.add(new Node("node-" + (i + 1), addresses[i], clusterName));
      }
   }

   /**
    * Every step guarded on its own. A container that fails to stop - a daemon hiccup, or one that is already
    * gone - must not keep the others running or leave the network behind, and a teardown failure must not
    * replace a real assertion failure in the surefire report with a Docker error.
    */
   @AfterEach
   public void after() {
      for (Node node : nodes) {
         try {
            node.close();
         } catch (RuntimeException e) {
            System.out.println("Could not stop " + node.name + ": " + e);
         }
      }
      nodes.clear();
      if (networkId != null) {
         try {
            docker.removeNetworkCmd(networkId).exec();
         } catch (RuntimeException e) {
            System.out.println("Could not remove the cluster network: " + e);
         }
         networkId = null;
      }
   }

   @Test
   public void shouldConvergeAfterTheInterfaceIsTakenAwayAndGivenBack() {
      for (Node node : nodes) {
         node.start();
      }
      // Only now is the cluster network attached: a channel created before this would have bound to an address
      // the container does not have, which is why joining is a command rather than something main() does.
      for (Node node : nodes) {
         node.connectToCluster();
      }
      for (Node node : nodes) {
         node.send("join");
         node.id = node.send("id");
      }
      awaitCluster("the cluster forms over real TCP", nodes);

      // A registration per node before the split, so the merge has pre-existing state to preserve as well as
      // state created while the cluster was broken.
      for (Node node : nodes) {
         node.send("register " + PRE + " 1");
      }
      String everyone = expected(nodes, 1);
      for (Node node : nodes) {
         awaitPayload("every node sees every pre-split registration", node, "registrations " + PRE, everyone);
      }

      Node isolated = nodes.get(0);
      List<Node> majority = nodes.subList(1, nodes.size());

      long splitAt = System.nanoTime();
      isolated.disconnectFromCluster();
      await("the view splits once the interface is gone", SPLIT_DEADLINE_MS,
            () -> isolated.send("nodes").equals(isolated.id)
                  && majority.stream().allMatch(node -> node.send("nodes").equals(expectedNodes(majority))));
      // Printed because the number is the evidence for what detected the split, and the class javadoc leans on
      // it: around 40s says FD_ALL3's timeout, anything near zero would mean a socket actually closed.
      System.out.printf("[container-partition] split observed in %d ms (FD_ALL3 at its shipped 8s/40s)%n",
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - splitAt));

      // Both sides write while they cannot see each other. This is what the merge has to reconcile, and it only
      // works because each side is the single writer of what it wrote.
      isolated.send("register " + DURING + " 2");
      majority.get(0).send("register " + DURING + " 2");
      isolated.send("setnodeinfo 10.0.0.1 " + JGROUPS_PORT);
      majority.get(0).send("setnodeinfo 10.0.0.2 " + JGROUPS_PORT);

      // Asserting the split is real at the application level, not just in the view: neither side may have
      // picked up the other's write. Without this the convergence below could pass on a partition that never
      // actually stopped any traffic.
      assertEquals(expected(List.of(isolated), 2), isolated.send("registrations " + DURING),
            "the isolated node saw a write from the other side of the partition");
      for (Node node : majority) {
         awaitPayload("the majority agrees on its own write", node, "registrations " + DURING,
               expected(List.of(majority.get(0)), 2));
      }

      long healedAt = System.nanoTime();
      isolated.connectToCluster();
      // Against the ids captured at join, so this also says the node that came back is the same node rather
      // than a replacement - a restarted JVM would rejoin under a new logical name and be a join, not a merge.
      awaitCluster("the cluster merges once the interface is back", nodes);
      System.out.printf("[container-partition] merged in %d ms%n",
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - healedAt));

      // The reconciliation itself: everything written on either side of a broken connection, on every node.
      String bothSides = expected(List.of(isolated, majority.get(0)), 2);
      for (Node node : nodes) {
         awaitPayload("every pre-split registration survives the merge", node, "registrations " + PRE, everyone);
         awaitPayload("both sides' writes converge after the merge", node, "registrations " + DURING, bothSides);
      }
      // Node info travels the same slices and has the same single writer, but it is a replace rather than an
      // add, so a merge that applied a stale slice would show up here and nowhere else.
      for (Node node : nodes) {
         awaitPayload("the isolated node's info reaches everyone", node, "nodeinfo " + isolated.id,
               "10.0.0.1:" + JGROUPS_PORT + ":" + isolated.id);
         awaitPayload("the majority's node info reaches everyone", node, "nodeinfo " + majority.get(0).id,
               "10.0.0.2:" + JGROUPS_PORT + ":" + majority.get(0).id);
      }
   }

   private void awaitCluster(String what, List<Node> members) {
      String all = expectedNodes(members);
      for (Node node : members) {
         awaitPayload(what, node, "nodes", all);
      }
   }

   private static String expectedNodes(List<Node> members) {
      return String.join(",", members.stream().map(node -> node.id).sorted().toList());
   }

   /** The reply {@code registrations} is expected to give for a set of nodes that each registered at {@code seq}. */
   private static String expected(List<Node> members, long seq) {
      return String.join(",", members.stream().map(node -> node.id + ":" + seq).sorted().toList());
   }

   private void awaitPayload(String what, Node node, String command, String expected) {
      await(what + " (" + node.name + ": " + command + ")", AWAIT_MS, () -> expected.equals(node.send(command)));
      // Re-read outside the poll so a failure reports what was actually there rather than just "timed out".
      assertEquals(expected, node.send(command), what + " at " + node.name);
   }

   private void await(String what, long timeoutMs, Supplier<Boolean> condition) {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
      while (System.nanoTime() < deadline) {
         if (condition.get()) {
            return;
         }
         try {
            Thread.sleep(100);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for " + what);
         }
      }
      fail("Timed out after " + timeoutMs + "ms waiting for " + what);
   }

   /** A container running one {@link ContainerNode}, plus the control connection used to drive it. */
   private final class Node implements Closeable {
      private final String name;
      private final String address;
      private final GenericContainer<?> container;
      private Socket socket;
      private BufferedReader in;
      private PrintWriter out;
      private String id;

      Node(String name, String address, String clusterName) {
         this.name = name;
         this.address = address;
         Path target = moduleTarget();
         // Mounted rather than baked into an image: the module's own output is what is under test, and a bind
         // mount keeps the test honest about which classes it ran as well as keeping the image a stock JDK.
         this.container = new GenericContainer<>(DockerImageName.parse(IMAGE))
               .withExposedPorts(CONTROL_PORT)
               .withFileSystemBind(requireBuilt(target.resolve("classes")), "/app/classes", BindMode.READ_ONLY)
               .withFileSystemBind(requireBuilt(target.resolve("test-classes")), "/app/test-classes",
                     BindMode.READ_ONLY)
               .withFileSystemBind(requireBuilt(target.resolve("dependency")), "/app/libs", BindMode.READ_ONLY)
               // The image's entrypoint is a launcher script that expects a packaged application.
               .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"))
               .withCommand("-cp", "/app/classes:/app/test-classes:/app/libs/*",
                     // Everything the shipped stack needs, which is why it needs no test-only copy. The
                     // addresses are the ones the cluster network is about to hand out.
                     "-Djgroups.bind.address=" + address,
                     "-Djgroups.bind.port=" + JGROUPS_PORT,
                     "-Djgroups.tcpping.initial_hosts=" + initialHosts(),
                     "-Dnode.cluster.name=" + clusterName,
                     "-Dnode.control.port=" + CONTROL_PORT,
                     ContainerNode.class.getName())
               .waitingFor(Wait.forLogMessage(".*ContainerNode listening.*\\n", 1)
                     .withStartupTimeout(Duration.ofMinutes(2)))
               // Straight to stdout rather than through a logger: there is no SLF4J provider on this module's
               // test classpath, and when this test fails on a machine nobody can attach to, the three nodes'
               // view changes in the surefire output are the only thing that explains why.
               .withLogConsumer(frame -> System.out.print("[" + name + "] " + frame.getUtf8String()));
      }

      void start() {
         container.start();
         try {
            socket = new Socket(container.getHost(), container.getMappedPort(CONTROL_PORT));
            socket.setTcpNoDelay(true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
         } catch (IOException e) {
            throw new UncheckedIOException("Cannot reach the control socket of " + name, e);
         }
      }

      void connectToCluster() {
         docker.connectToNetworkCmd()
               .withNetworkId(networkId)
               .withContainerId(container.getContainerId())
               // The same address every time: TCPPING's initial_hosts was fixed before the container started.
               // It has to go through the endpoint's IPAM config - ContainerNetwork.withIpv4Address is the
               // address the daemon reports back, and setting it on the request is quietly ignored.
               .withContainerNetwork(new ContainerNetwork()
                     .withIpamConfig(new ContainerNetwork.Ipam().withIpv4Address(address)))
               .exec();
      }

      void disconnectFromCluster() {
         docker.disconnectFromNetworkCmd()
               .withNetworkId(networkId)
               .withContainerId(container.getContainerId())
               .exec();
      }

      String send(String command) {
         try {
            out.println(command);
            String reply = in.readLine();
            if (reply == null) {
               throw new AssertionError(name + " closed the control connection on '" + command + "'");
            }
            if (!reply.startsWith("OK ")) {
               throw new AssertionError(name + " failed '" + command + "': " + reply);
            }
            return reply.substring(3);
         } catch (IOException e) {
            throw new UncheckedIOException("Control connection to " + name + " failed on '" + command + "'", e);
         }
      }

      @Override
      public void close() {
         try {
            if (socket != null) {
               socket.close();
            }
         } catch (IOException e) {
            // closing down anyway
         }
         container.stop();
      }
   }

   private String initialHosts() {
      List<String> hosts = new ArrayList<>();
      for (String address : addresses) {
         hosts.add(address + "[" + JGROUPS_PORT + "]");
      }
      return String.join(",", hosts);
   }

   /**
    * The module's {@code target}, found through this class's own location rather than the working directory, so
    * that the test does not depend on how it was launched.
    */
   private static Path moduleTarget() {
      try {
         return Path.of(JGroupsContainerPartitionTest.class.getProtectionDomain().getCodeSource().getLocation()
               .toURI()).getParent();
      } catch (URISyntaxException e) {
         throw new AssertionError("Cannot locate the module's build output", e);
      }
   }

   private static String requireBuilt(Path path) {
      assertTrue(Files.isDirectory(path),
            path + " does not exist; the containers run the module's build output, so it has to be built first");
      return path.toString();
   }
}

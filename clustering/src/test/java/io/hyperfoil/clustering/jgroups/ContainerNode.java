package io.hyperfoil.clustering.jgroups;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * One {@link JGroupsClusterManager}, alone in a JVM, driven from outside over a line-based TCP control socket.
 * This is the in-container half of {@link JGroupsContainerPartitionTest}; it is never run by surefire.
 * <p>
 * It exists because the partition that test induces is a {@code docker network disconnect} - the container's
 * interface is taken away underneath the JVM. Nothing in the JVM can ask for that, so the node has to be a
 * process the test can start, address and cut off from the outside, and the control socket is the narrowest
 * thing that makes a {@code ClusterManager} drivable that way: one line in, one line out, no RPC framework and
 * no serialization to get wrong. Keeping it to one {@code main} also keeps the container a stock JDK image with
 * the module's own output mounted into it, rather than an image that has to be built before the test can run.
 * <p>
 * Addressing comes from system properties because the shipped {@code jgroups-tcp.xml} already reads all of it
 * from system properties - see {@link JGroupsContainerPartitionTest}, which is the only test in the module
 * running that file rather than a copy of it. Joining, though, is a command rather than something {@code main}
 * does on its own: the cluster network is attached after the container is up, and a channel created before that
 * would bind to an address the container does not have yet.
 * <p>
 * Protocol: one command per line, one reply per line, {@code OK <payload>} or {@code ERR <message>}. The payload
 * is empty rather than absent when there is nothing to say, so the reader can always drop the first three
 * characters. Commands are served one at a time on a single connection, which is all the test needs and is one
 * fewer thing to reason about than a concurrent server would be.
 *
 * <table border="1">
 * <caption>Commands</caption>
 * <tr>
 * <td>{@code join}</td>
 * <td>creates the channel and joins; replies with this node's id</td>
 * </tr>
 * <tr>
 * <td>{@code id}</td>
 * <td>this node's id</td>
 * </tr>
 * <tr>
 * <td>{@code nodes}</td>
 * <td>the ids this node currently sees, sorted, comma separated</td>
 * </tr>
 * <tr>
 * <td>{@code register <address> <seq>}</td>
 * <td>adds a registration of this node on {@code address}</td>
 * </tr>
 * <tr>
 * <td>{@code registrations <address>}</td>
 * <td>{@code nodeId:seq} pairs, sorted, comma separated</td>
 * </tr>
 * <tr>
 * <td>{@code setnodeinfo <host> <port>}</td>
 * <td>sets this node's info, tagged with this node's id</td>
 * </tr>
 * <tr>
 * <td>{@code nodeinfo <nodeId>}</td>
 * <td>{@code host:port:tag} for that node, or empty if unknown</td>
 * </tr>
 * <tr>
 * <td>{@code leave}</td>
 * <td>leaves the cluster gracefully</td>
 * </tr>
 * </table>
 */
public final class ContainerNode {
   /** The shipped controller stack, unmodified. Everything it needs is passed as a system property. */
   private static final String STACK = "jgroups-tcp.xml";
   private static final long TIMEOUT_MS = 60_000;

   private Vertx vertx;
   private JGroupsClusterManager manager;

   public static void main(String[] args) throws IOException {
      int port = Integer.getInteger("node.control.port", 9000);
      ContainerNode node = new ContainerNode();
      try (ServerSocket server = new ServerSocket(port)) {
         // The test waits for this line before it does anything else, so it must be printed after the bind.
         System.out.println("ContainerNode listening on " + port);
         while (!Thread.currentThread().isInterrupted()) {
            try (Socket socket = server.accept()) {
               node.serve(socket);
            } catch (IOException e) {
               System.out.println("Control connection failed: " + e);
            }
         }
      }
   }

   private void serve(Socket socket) throws IOException {
      socket.setTcpNoDelay(true);
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),
            StandardCharsets.UTF_8), true);
      String line;
      while ((line = in.readLine()) != null) {
         String reply;
         try {
            reply = "OK " + execute(line.trim().split(" "));
         } catch (Throwable t) {
            // Reply rather than die: a command failing is something the test should see as an assertion
            // failure naming the node, not as a connection that silently drops.
            System.out.println("Command '" + line + "' failed: " + t);
            t.printStackTrace(System.out);
            reply = "ERR " + t;
         }
         out.println(reply);
      }
   }

   private String execute(String[] command) throws Exception {
      switch (command[0]) {
         case "join":
            return join();
         case "id":
            return manager().getNodeId();
         case "nodes":
            return String.join(",", manager().getNodes().stream().sorted().toList());
         case "register":
            return register(command[1], Long.parseLong(command[2]));
         case "registrations":
            return registrations(command[1]);
         case "setnodeinfo":
            return setNodeInfo(command[1], Integer.parseInt(command[2]));
         case "nodeinfo":
            return nodeInfo(command[1]);
         case "leave":
            return leave();
         default:
            throw new IllegalArgumentException("Unknown command: " + command[0]);
      }
   }

   private String join() throws Exception {
      if (manager != null) {
         throw new IllegalStateException("Already joined as " + manager.getNodeId());
      }
      vertx = Vertx.vertx();
      JGroupsClusterManager joining = new JGroupsClusterManager(STACK, System.getProperty("node.cluster.name"));
      joining.init(vertx);
      Promise<Void> promise = Promise.promise();
      joining.join(promise);
      await(promise.future());
      manager = joining;
      return manager.getNodeId();
   }

   private String leave() throws Exception {
      Promise<Void> promise = Promise.promise();
      manager().leave(promise);
      await(promise.future());
      manager = null;
      await(vertx.close());
      vertx = null;
      return "";
   }

   private String register(String address, long seq) throws Exception {
      Promise<Void> promise = Promise.promise();
      manager().addRegistration(address, new RegistrationInfo(manager().getNodeId(), seq, false), promise);
      await(promise.future());
      return "";
   }

   private String registrations(String address) throws Exception {
      Promise<List<RegistrationInfo>> promise = Promise.promise();
      manager().getRegistrations(address, promise);
      List<String> encoded = new ArrayList<>();
      for (RegistrationInfo registration : await(promise.future())) {
         encoded.add(registration.nodeId() + ":" + registration.seq());
      }
      // Sorted so that the test can compare the reply as a string: the SPI makes no ordering promise and the
      // order a node happens to hold its registrations in is not what is under test.
      return String.join(",", encoded.stream().sorted().toList());
   }

   private String setNodeInfo(String host, int port) throws Exception {
      // Tagging the metadata with the node id is what makes a stale read visible: without it every node's info
      // would differ only by host and port, which a test could get right by accident.
      JsonObject metadata = new JsonObject().put("tag", manager().getNodeId());
      Promise<Void> promise = Promise.promise();
      manager().setNodeInfo(new NodeInfo(host, port, metadata), promise);
      await(promise.future());
      return "";
   }

   private String nodeInfo(String nodeId) throws Exception {
      Promise<NodeInfo> promise = Promise.promise();
      manager().getNodeInfo(nodeId, promise);
      // getNodeInfo fails rather than returning null when the node is unknown, and "unknown yet" is a state the
      // test polls through rather than an error, so it is reported as an empty payload.
      Future<NodeInfo> future = promise.future();
      if (!future.isComplete() || future.failed()) {
         return "";
      }
      NodeInfo info = future.result();
      return info.host() + ":" + info.port() + ":" + info.metadata().getString("tag");
   }

   private JGroupsClusterManager manager() {
      if (manager == null) {
         throw new IllegalStateException("Not joined yet");
      }
      return manager;
   }

   private static <T> T await(Future<T> future) throws Exception {
      return future.toCompletionStage().toCompletableFuture().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }
}

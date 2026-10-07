package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.TCP;
import org.jgroups.stack.ProtocolStack;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * {@link JGroupsClusterManagerPartitionTest#shouldConvergeAfterHealingAPartition} over real TCP sockets instead
 * of SHARED_LOOPBACK.
 * <p>
 * The reconciliation logic is transport-independent and the SHARED_LOOPBACK test already pins it down, so what
 * this adds is everything underneath: three channels really bind sockets, really discover each other through
 * TCPPING, and really have to re-establish connections and rediscover after the split heals. SHARED_LOOPBACK
 * hands messages between stacks through a static per-JVM registry, so none of that code runs there - a merge
 * that works in-registry but deadlocks on socket reconnection would pass the other test.
 * <p>
 * Kept separate from {@link JGroupsClusterManagerPartitionTest} rather than parameterised onto it: this one
 * binds real ports, so it can collide with anything else on the machine, and it is the slower of the two.
 * <p>
 * <b>Every view this test installs is a {@code MergeView}, including the ones that build the cluster in the
 * first place.</b> Three channels starting at once over TCPPING all fail to discover a coordinator - the
 * shipped stack sets {@code join_timeout="0"} - so each forms a singleton view and MERGE3 stitches the three
 * together a second or two later. That is not a flaw in the setup, it is the case the stack's own MERGE3
 * comment is about, and it makes merge reconciliation load-bearing for ordinary startup rather than only for
 * split-brain. It also means the assertions before the split already depend on the announcement: disabling it
 * for {@code MergeView} alone, leaving plain joins untouched, reproducibly hangs this test at the first
 * assertion rather than at the one after the heal.
 */
public class JGroupsTcpPartitionTest extends ClusterManagerTestBase {
   private static final String CONTROL = "control-feed";

   @Override
   protected String stack() {
      return "jgroups-tcp-test.xml";
   }

   private static NodeInfo nodeInfo(int port) {
      return new NodeInfo("127.0.0.1", port, new JsonObject().put("port", port));
   }

   @Test
   public void shouldConvergeAfterHealingATcpPartition() throws Exception {
      // Unique per run, because the ports are not. bind_port/port_range and TCPPING's initial_hosts are fixed
      // in the stack file, so two builds on one machine - a second CI job, a developer building alongside it,
      // surefire with forkCount>1 - put six channels on the same loopback ports and discover each other. Only
      // the cluster name keeps them apart: discovery responses carry it and GMS refuses a join across names,
      // so with a constant name the two runs merge into one six-member view and both fail on member counts
      // that have nothing to do with what is under test.
      String cluster = "tcp-partition-" + UUID.randomUUID();
      JGroupsClusterManager a = join(cluster);
      JGroupsClusterManager b = join(cluster);
      JGroupsClusterManager c = join(cluster);

      RegistrationInfo beforeSplit = new RegistrationInfo(c.getNodeId(), 1, false);
      setNodeInfo(c, nodeInfo(3333));
      addRegistration(c, CONTROL, beforeSplit);
      assertRegistrations("C's registration at A before the split", a, CONTROL, Set.of(beforeSplit));

      DISCARD discard = isolate(a);
      try {
         // FD_ALL3 has to time out here - DISCARD drops messages without closing sockets, so FD_SOCK2 sees
         // nothing wrong and cannot short-circuit it the way it does for a killed process.
         awaitTrue("A alone in its own view", () -> a.getNodes().size() == 1);
         awaitTrue("A evicted from the majority view", () -> b.getNodes().size() == 2);
         awaitTrue("C's registration purged at A", () -> registrations(a, CONTROL).isEmpty());
         assertNull(nodeInfoOf(a, c.getNodeId()));

         // Both sides keep accepting writes while split, so the merge has something to reconcile in both directions
         RegistrationInfo fromA = new RegistrationInfo(a.getNodeId(), 2, false);
         RegistrationInfo fromB = new RegistrationInfo(b.getNodeId(), 1, false);
         setNodeInfo(a, nodeInfo(1111));
         addRegistration(a, CONTROL, fromA);
         addRegistration(b, CONTROL, fromB);
         assertEquals(List.of(fromA), registrations(a, CONTROL));
         assertRegistrations("B and C's view of the majority side", b, CONTROL, Set.of(beforeSplit, fromB));

         discard.discardAll(false);

         Set<RegistrationInfo> all = Set.of(fromA, fromB, beforeSplit);
         for (JGroupsClusterManager manager : List.of(a, b, c)) {
            awaitTrue("merged view at " + manager.getNodeId(), () -> manager.getNodes().size() == 3);
            assertRegistrations("all registrations at " + manager.getNodeId(), manager, CONTROL, all);
            // Set equality would hide a duplicate, which is what a merge replay would produce
            assertEquals(all.size(), registrations(manager, CONTROL).size(),
                  "duplicate registrations at " + manager.getNodeId());
            assertEquals(nodeInfo(1111), awaitValue("A's node info at " + manager.getNodeId(),
                  () -> nodeInfoOf(manager, a.getNodeId())));
            assertEquals(nodeInfo(3333), awaitValue("C's node info at " + manager.getNodeId(),
                  () -> nodeInfoOf(manager, c.getNodeId())));
         }
      } finally {
         // An assertion failing while A is still isolated would otherwise take the port with it: teardown's
         // disconnect() sends a GMS LEAVE that DISCARD swallows, the leave blocks until it gives up, and the
         // socket stays bound long enough for the next test to fail to bind for an unrelated-looking reason.
         heal(a, discard);
      }
   }

   /**
    * Cuts one node off in both directions. Inserted directly above the {@link TCP} transport, so the sockets
    * themselves stay up and only the messages crossing them are lost - a partition rather than a crash.
    */
   private static DISCARD isolate(JGroupsClusterManager manager) throws Exception {
      DISCARD discard = new DISCARD().discardAll(true);
      manager.channel().getProtocolStack()
            .insertProtocol(discard, ProtocolStack.Position.ABOVE, TCP.class);
      return discard;
   }

   /** Undoes {@link #isolate}, leaving the stack as it was found. Safe to call twice. */
   private static void heal(JGroupsClusterManager manager, DISCARD discard) {
      discard.discardAll(false);
      ProtocolStack stack = manager.channel().getProtocolStack();
      if (stack.findProtocol(DISCARD.class) != null) {
         stack.removeProtocol(discard);
      }
   }
}

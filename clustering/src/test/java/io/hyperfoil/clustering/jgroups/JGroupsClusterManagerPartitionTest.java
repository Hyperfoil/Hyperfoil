package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.stack.ProtocolStack;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * State repair, driven with {@link DISCARD}. Both tests here cover the same mechanism from opposite ends: a node
 * announces its own slice whenever the view gains a member, which is the only thing that repairs a node that
 * missed something. One test loses a whole side of the cluster to a partition, the other loses a single state
 * pull response.
 * <p>
 * Takes ~20s: the stack has to detect a split, let MERGE3 find its way back, and elsewhere wait out a pull that
 * nobody answers.
 */
public class JGroupsClusterManagerPartitionTest extends ClusterManagerTestBase {
   private static final String CONTROL = "control-feed";

   private static NodeInfo nodeInfo(int port) {
      return new NodeInfo("127.0.0.1", port, new JsonObject().put("port", port));
   }

   /**
    * The shipped stacks run MERGE3 at {@code min_interval=1000 max_interval=3000}, so merges are a routine event
    * on a loaded or flaky network rather than an exotic one. Nothing re-pulls state on a merge; each side simply
    * re-announces its own slice, which is what this asserts.
    */
   @Test
   public void shouldConvergeAfterHealingAPartition() throws Exception {
      String cluster = "partition";
      JGroupsClusterManager a = join(cluster);
      JGroupsClusterManager b = join(cluster);
      JGroupsClusterManager c = join(cluster);

      RegistrationInfo beforeSplit = new RegistrationInfo(c.getNodeId(), 1, false);
      setNodeInfo(c, nodeInfo(3333));
      addRegistration(c, CONTROL, beforeSplit);
      assertRegistrations("C's registration at A before the split", a, CONTROL, Set.of(beforeSplit));

      DISCARD discard = isolate(a);
      awaitTrue("A alone in its own view", () -> a.getNodes().size() == 1);
      awaitTrue("A evicted from B's view", () -> b.getNodes().size() == 2);
      // Each side purges what it can no longer see
      awaitTrue("C's registration purged at A", () -> registrations(a, CONTROL).isEmpty());
      assertNull(nodeInfoOf(a, c.getNodeId()));

      // Both sides keep working in isolation
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
   }

   /**
    * A joiner's state pull completes with whatever answered before its deadline, and a peer that missed the
    * deadline is then simply absent from the joiner's state with nothing left to retry. Here B never answers at
    * all, and the joiner must still end up knowing B - repaired by the slice B announces when it sees the joiner
    * appear in the view.
    */
   @Test
   public void shouldRepairAJoinerWhoseStatePullWasLost() throws Exception {
      String cluster = "lost-pull";
      JGroupsClusterManager a = join(cluster);
      JGroupsClusterManager b = join(cluster);

      RegistrationInfo fromB = new RegistrationInfo(b.getNodeId(), 1, false);
      addRegistration(b, CONTROL, fromB);
      setNodeInfo(b, nodeInfo(2222));
      assertRegistrations("B's registration at A", a, CONTROL, Set.of(fromB));

      DISCARD discard = muteUnicasts(b);
      try {
         // Joining takes the full state-pull timeout: A answers, B cannot, and the pull waits out the deadline.
         JGroupsClusterManager c = join(cluster);

         assertRegistrations("B's registration at the joiner", c, CONTROL, Set.of(fromB));
         assertEquals(nodeInfo(2222),
               awaitValue("B's node info at the joiner", () -> nodeInfoOf(c, b.getNodeId())));
      } finally {
         b.channel().getProtocolStack().removeProtocol(discard);
      }
   }

   /**
    * Drops everything the node sends point to point, while leaving its multicasts alone - so its answer to a
    * state pull is lost but the slice it announces is not.
    * <p>
    * Inserted above {@code UNICAST3} rather than above the transport, which is the whole point: below it the
    * dropped response would be retransmitted and the pull would quietly succeed.
    */
   private static DISCARD muteUnicasts(JGroupsClusterManager manager) throws Exception {
      DISCARD discard = new DISCARD().dropDownUnicasts(Integer.MAX_VALUE);
      manager.channel().getProtocolStack()
            .insertProtocol(discard, ProtocolStack.Position.ABOVE, UNICAST3.class);
      return discard;
   }

   /** Cuts one node off in both directions, leaving the rest of the cluster to form its own view. */
   private static DISCARD isolate(JGroupsClusterManager manager) throws Exception {
      DISCARD discard = new DISCARD().discardAll(true);
      manager.channel().getProtocolStack()
            .insertProtocol(discard, ProtocolStack.Position.ABOVE, SHARED_LOOPBACK.class);
      return discard;
   }
}

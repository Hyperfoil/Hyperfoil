package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

public class ClusterStateTest {
   private static final String A = "node-a";
   private static final String B = "node-b";
   private static final String CONTROL = "control-feed";
   private static final String STATS = "stats-feed";

   private static RegistrationInfo reg(String nodeId, long seq) {
      return new RegistrationInfo(nodeId, seq, false);
   }

   private static NodeInfo info(int port) {
      return new NodeInfo("10.0.0.1", port, null);
   }

   @Test
   public void shouldRejectStaleUpdates() {
      ClusterState state = new ClusterState();
      assertTrue(state.applyNodeInfo(A, 5, info(5)));

      assertFalse(state.applyNodeInfo(A, 5, info(99)), "same version must not overwrite");
      assertFalse(state.applyNodeInfo(A, 4, info(99)), "older version must not overwrite");
      assertEquals(info(5), state.nodeInfo(A));

      assertFalse(state.applySubAdd(A, 3, CONTROL, reg(A, 1)));
      assertTrue(state.registrations(CONTROL).isEmpty());

      assertTrue(state.applySubAdd(A, 6, CONTROL, reg(A, 1)));
      assertEquals(List.of(reg(A, 1)), state.registrations(CONTROL));
   }

   @Test
   public void shouldNotLetOneOwnerOverwriteAnother() {
      ClusterState state = new ClusterState();
      state.applyNodeInfo(A, 1, info(1));
      state.applySubAdd(A, 2, CONTROL, reg(A, 1));
      // B's versions are independent of A's and must not be compared against them
      state.applyNodeInfo(B, 1, info(2));
      state.applySubAdd(B, 2, CONTROL, reg(B, 1));

      assertEquals(info(1), state.nodeInfo(A));
      assertEquals(info(2), state.nodeInfo(B));
      assertEquals(Set.of(reg(A, 1), reg(B, 1)), Set.copyOf(state.registrations(CONTROL)));
   }

   @Test
   public void shouldPurgeExactlyOneOwner() {
      ClusterState state = new ClusterState();
      state.applyNodeInfo(A, 1, info(1));
      state.applySubAdd(A, 2, CONTROL, reg(A, 1));
      state.applySubAdd(A, 3, STATS, reg(A, 2));
      state.applyNodeInfo(B, 1, info(2));
      state.applySubAdd(B, 2, CONTROL, reg(B, 1));

      assertEquals(Set.of(CONTROL, STATS), state.purge(A));

      assertNull(state.nodeInfo(A));
      assertEquals(info(2), state.nodeInfo(B));
      assertEquals(List.of(reg(B, 1)), state.registrations(CONTROL));
      assertTrue(state.registrations(STATS).isEmpty());

      assertTrue(state.purge(A).isEmpty(), "purging an unknown node is a no-op");
   }

   @Test
   public void shouldKeepLocalOnlyRegistrationsOutOfTheSlice() {
      ClusterState state = new ClusterState();
      RegistrationInfo local = new RegistrationInfo(A, 1, true);
      state.addLocalOnly(CONTROL, local);
      state.applySubAdd(A, 1, CONTROL, reg(A, 2));

      assertEquals(Set.of(local, reg(A, 2)), Set.copyOf(state.registrations(CONTROL)));

      // The slice we hand to a joining peer must carry only the replicated registration
      StateMessages.Reader reader = new StateMessages.Reader(state.encodeSlice(A));
      reader.op();
      reader.readLong();
      reader.readByte();
      assertEquals(1, reader.readInt());
      assertEquals(CONTROL, reader.readString());
      assertEquals(1, reader.readInt());
      assertEquals(reg(A, 2), reader.readRegistration());

      state.removeLocalOnly(CONTROL, local);
      assertEquals(List.of(reg(A, 2)), state.registrations(CONTROL));
   }

   @Test
   public void shouldSurviveARemoveOfAnUnknownRegistration() {
      ClusterState state = new ClusterState();
      assertTrue(state.applySubRemove(A, 1, CONTROL, reg(A, 1)), "version must advance even with nothing to remove");
      assertTrue(state.registrations(CONTROL).isEmpty());
      state.removeLocalOnly(STATS, new RegistrationInfo(A, 1, true));
      assertTrue(state.registrations(STATS).isEmpty());
   }

   @Test
   public void shouldReapplyASliceAtTheSameVersion() {
      // A peer re-announcing state we already hold must repair us, not be dropped as a duplicate. This is the
      // merge case: A was purged while the two halves were apart, and the view that reunites them admits it
      // again before the slice it announces arrives.
      ClusterState state = new ClusterState();
      state.applySlice(A, 7, info(1), Map.of(CONTROL, Set.of(reg(A, 1))));
      state.purge(A);
      state.admit(A);

      assertEquals(Set.of(CONTROL), state.applySlice(A, 7, info(1), Map.of(CONTROL, Set.of(reg(A, 1)))));
      assertEquals(List.of(reg(A, 1)), state.registrations(CONTROL));
   }

   /**
    * The reason {@link ClusterState#purge} leaves a tombstone. A delta that was in flight - or queued behind a
    * state pull, which is the race that makes this reachable rather than theoretical - must not recreate the
    * slice of a node that has left. Nothing would ever purge it a second time: the owner is in no view, so no
    * later membership diff names it, and the event bus would go on routing to it for good.
    */
   @Test
   public void shouldNotLetALateUpdateResurrectAPurgedOwner() {
      ClusterState state = new ClusterState();
      state.applySubAdd(A, 1, CONTROL, reg(A, 1));
      state.applyNodeInfo(A, 2, info(1));
      assertEquals(Set.of(CONTROL), state.purge(A));

      assertFalse(state.applySubAdd(A, 3, CONTROL, reg(A, 1)), "a delta must not bring a departed owner back");
      assertFalse(state.applyNodeInfo(A, 4, info(1)));
      assertTrue(state.applySlice(A, 9, info(1), Map.of(CONTROL, Set.of(reg(A, 1)))).isEmpty(),
            "nor must an announced slice");
      assertTrue(state.registrations(CONTROL).isEmpty());
      assertNull(state.nodeInfo(A));

      // ...until the view says it is back, which is the only thing that may undo a purge
      state.admit(A);
      assertTrue(state.applySubAdd(A, 3, CONTROL, reg(A, 1)));
      assertEquals(List.of(reg(A, 1)), state.registrations(CONTROL));
   }

   /** A purge must not strand an owner we never held anything for: its slice may still be on the way. */
   @Test
   public void shouldTombstoneAnOwnerItHeldNothingFor() {
      ClusterState state = new ClusterState();
      assertTrue(state.purge(B).isEmpty());

      assertFalse(state.applySubAdd(B, 1, CONTROL, reg(B, 1)));
      assertTrue(state.registrations(CONTROL).isEmpty());
   }

   @Test
   public void shouldDropASliceOlderThanWhatWeHold() {
      ClusterState state = new ClusterState();
      state.applySlice(A, 9, info(1), Map.of(CONTROL, Set.of(reg(A, 1)), STATS, Set.of(reg(A, 2))));

      assertTrue(state.applySlice(A, 8, info(2), Map.of()).isEmpty());
      assertEquals(info(1), state.nodeInfo(A));
      assertEquals(List.of(reg(A, 1)), state.registrations(CONTROL));
   }

   /**
    * The invariant {@link JGroupsClusterManager} queues incoming updates to preserve: a pulled slice is the base and
    * the deltas that raced it apply on top. Applying the same delta before the slice instead would leave the slice
    * looking stale, so the manager must never let that order happen.
    */
   @Test
   public void shouldConvergeWhenDeltasFollowTheSlice() {
      Map<String, Set<RegistrationInfo>> base = Map.of(CONTROL, Set.of(reg(A, 1)));

      ClusterState viaSliceOnly = new ClusterState();
      viaSliceOnly.applySlice(A, 6, info(1), Map.of(CONTROL, Set.of(reg(A, 1)), STATS, Set.of(reg(A, 2))));

      ClusterState viaSliceThenDelta = new ClusterState();
      viaSliceThenDelta.applySlice(A, 5, info(1), base);
      viaSliceThenDelta.applySubAdd(A, 6, STATS, reg(A, 2));

      assertEquals(viaSliceOnly.nodeInfo(A), viaSliceThenDelta.nodeInfo(A));
      assertEquals(viaSliceOnly.registrations(CONTROL), viaSliceThenDelta.registrations(CONTROL));
      assertEquals(viaSliceOnly.registrations(STATS), viaSliceThenDelta.registrations(STATS));
      // Not only the same registrations, but the same slice to hand on: this node is now as good a source of
      // A's state as the one it pulled from, which is what lets the announcement repair an arbitrary peer.
      assertEquals(decodeSlice(viaSliceOnly.encodeSlice(A)), decodeSlice(viaSliceThenDelta.encodeSlice(A)));
   }

   private record DecodedSlice(long version, NodeInfo nodeInfo, Map<String, Set<RegistrationInfo>> subs) {
   }

   /**
    * Decoded rather than compared as bytes, because the encoding iterates a {@link java.util.HashMap} and two
    * states that agree can still serialize their addresses in a different order.
    */
   private static DecodedSlice decodeSlice(byte[] encoded) {
      StateMessages.Reader reader = new StateMessages.Reader(encoded);
      assertEquals(StateMessages.OP_SLICE, reader.op());
      long version = reader.readLong();
      NodeInfo nodeInfo = reader.readByte() == 1 ? reader.readNodeInfo() : null;
      Map<String, Set<RegistrationInfo>> subs = new HashMap<>();
      int addressCount = reader.readInt();
      for (int i = 0; i < addressCount; ++i) {
         String address = reader.readString();
         Set<RegistrationInfo> registrations = new HashSet<>();
         int registrationCount = reader.readInt();
         for (int j = 0; j < registrationCount; ++j) {
            registrations.add(reader.readRegistration());
         }
         subs.put(address, registrations);
      }
      return new DecodedSlice(version, nodeInfo, subs);
   }

   @Test
   public void shouldDropDeltasAlreadyContainedInTheSlice() {
      // The queued delta is replayed after the slice that already includes it; the version check must discard it
      ClusterState state = new ClusterState();
      state.applySlice(A, 6, info(1), Map.of(CONTROL, Set.of(reg(A, 1)), STATS, Set.of(reg(A, 2))));

      assertFalse(state.applySubAdd(A, 6, STATS, reg(A, 2)));
      assertEquals(List.of(reg(A, 2)), state.registrations(STATS));
   }

   @Test
   public void shouldEncodeAnEmptySliceForAnUnknownNode() {
      StateMessages.Reader reader = new StateMessages.Reader(new ClusterState().encodeSlice("nobody"));
      assertEquals(StateMessages.OP_SLICE, reader.op());
      assertEquals(0, reader.readLong());
      assertEquals(0, reader.readByte());
      assertEquals(0, reader.readInt());
   }
}

package io.hyperfoil.clustering.jgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * The cluster-wide state replicated between {@link JGroupsClusterManager} instances: one {@link Slice} per node,
 * holding that node's {@link NodeInfo} and its event bus subscriptions.
 * <p>
 * Every node is the sole writer of its own slice - {@code NodeInfo} is keyed by its owner and
 * {@link RegistrationInfo#nodeId()} is always the registering node - so there are no cross-node write conflicts and
 * last-writer-wins per owner is correct. Each owner stamps its mutations with a monotonically increasing version;
 * receivers drop anything not newer than what they already hold. Incremental mutations and announced full slices
 * travel as multicasts while pulled full slices arrive as unicast RPC responses, and there is no ordering guarantee
 * between those two channels, so the version stamp is what makes the state immune to reordering, duplication and
 * merge replays.
 * <p>
 * Mutations are serialized on this object's monitor; reads go straight to concurrent maps and take no lock.
 * Mutation frequency is low (node join/leave and consumer registration), so a single mutation lock is cheap and
 * avoids the divergence bugs that lock-free index maintenance invites.
 */
final class ClusterState {
   /**
    * How many departed owners we remember. Only messages still in flight when the owner left can be addressed to
    * a tombstoned owner, so the useful lifetime of an entry is milliseconds; the bound exists so that a
    * controller that outlives thousands of agents does not accumulate one string per agent forever.
    */
   private static final int MAX_TOMBSTONES = 256;

   private final Map<String, Slice> slices = new ConcurrentHashMap<>();
   /**
    * Owners {@link #purge} has dropped and {@link #admit} has not brought back.
    * <p>
    * Removing the slice is not enough on its own. A message from or about a departed owner can still be in
    * flight, or sitting in {@link JGroupsClusterManager}'s pending queue waiting for a state pull to finish, and
    * every {@code apply*} below would otherwise recreate the slice through {@code computeIfAbsent} at version 0
    * and re-publish its subscriptions. Nothing would purge it a second time - the owner is in no view, so no
    * later membership diff names it - so the event bus would keep routing to a node that is gone for the rest of
    * this process's life.
    * <p>
    * Guarded by this object's monitor like every other mutation, with insertion-ordered FIFO eviction.
    */
   private final Set<String> purged = Collections.newSetFromMap(new LinkedHashMap<>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
         return size() > MAX_TOMBSTONES;
      }
   });
   /** Registrations with {@link RegistrationInfo#localOnly()}: never versioned, never replicated. */
   private final Map<String, Set<RegistrationInfo>> localSubs = new HashMap<>();
   /** Derived from {@link #slices} plus {@link #localSubs}; rebuilt per affected address on every mutation. */
   private final Map<String, List<RegistrationInfo>> subsIndex = new ConcurrentHashMap<>();

   static final class Slice {
      long version;
      NodeInfo nodeInfo;
      final Map<String, Set<RegistrationInfo>> subs = new HashMap<>();
   }

   // ---------------------------------------------------------------- reads

   NodeInfo nodeInfo(String nodeId) {
      Slice slice = slices.get(nodeId);
      return slice == null ? null : slice.nodeInfo;
   }

   List<RegistrationInfo> registrations(String address) {
      return subsIndex.getOrDefault(address, Collections.emptyList());
   }

   synchronized byte[] encodeSlice(String nodeId) {
      Slice slice = slices.get(nodeId);
      if (slice == null) {
         return StateMessages.slice(0, null, Collections.emptyMap());
      }
      return StateMessages.slice(slice.version, slice.nodeInfo, slice.subs);
   }

   // ------------------------------------------------------------ mutations

   /**
    * @return true if the update was newer than what we hold and was therefore applied
    */
   synchronized boolean applyNodeInfo(String owner, long version, NodeInfo nodeInfo) {
      if (purged.contains(owner)) {
         return false;
      }
      Slice slice = slices.computeIfAbsent(owner, k -> new Slice());
      if (version <= slice.version) {
         return false;
      }
      slice.version = version;
      slice.nodeInfo = nodeInfo;
      return true;
   }

   synchronized boolean applySubAdd(String owner, long version, String address, RegistrationInfo registration) {
      if (purged.contains(owner)) {
         return false;
      }
      Slice slice = slices.computeIfAbsent(owner, k -> new Slice());
      if (version <= slice.version) {
         return false;
      }
      slice.version = version;
      slice.subs.computeIfAbsent(address, k -> new LinkedHashSet<>()).add(registration);
      rebuild(address);
      return true;
   }

   synchronized boolean applySubRemove(String owner, long version, String address, RegistrationInfo registration) {
      if (purged.contains(owner)) {
         return false;
      }
      Slice slice = slices.computeIfAbsent(owner, k -> new Slice());
      if (version <= slice.version) {
         return false;
      }
      slice.version = version;
      Set<RegistrationInfo> registrations = slice.subs.get(address);
      if (registrations != null) {
         registrations.remove(registration);
         if (registrations.isEmpty()) {
            slice.subs.remove(address);
         }
      }
      rebuild(address);
      return true;
   }

   /**
    * Applies a full slice, replacing whatever we held for that owner.
    *
    * @return the addresses whose registrations changed, empty if the slice was stale and ignored
    */
   synchronized Set<String> applySlice(String owner, long version, NodeInfo nodeInfo,
         Map<String, Set<RegistrationInfo>> subs) {
      if (purged.contains(owner)) {
         return Collections.emptySet();
      }
      Slice slice = slices.computeIfAbsent(owner, k -> new Slice());
      // '<' rather than '<=': re-announcing the same version must still repair a peer that missed it.
      if (version < slice.version) {
         return Collections.emptySet();
      }
      Set<String> affected = new HashSet<>(slice.subs.keySet());
      affected.addAll(subs.keySet());
      slice.version = version;
      slice.nodeInfo = nodeInfo;
      slice.subs.clear();
      slice.subs.putAll(subs);
      affected.forEach(this::rebuild);
      return affected;
   }

   /**
    * Drops everything we hold for a departed node. Every surviving node does this independently - there is no
    * coordinator whose death would lose the cleanup.
    *
    * @return the addresses whose registrations changed
    */
   synchronized Set<String> purge(String owner) {
      // Tombstoned even when we held nothing for it: the reason we hold nothing may be that the owner's slice
      // is still on its way to us, and that message must not land after the purge either.
      purged.add(owner);
      Slice slice = slices.remove(owner);
      if (slice == null) {
         return Collections.emptySet();
      }
      Set<String> affected = new HashSet<>(slice.subs.keySet());
      affected.forEach(this::rebuild);
      return affected;
   }

   /**
    * Lets a previously purged owner's updates be applied again, undoing the tombstone {@link #purge} leaves.
    * <p>
    * Only the view may call this, through {@link JGroupsClusterManager}: a node is back when the membership says
    * it is back, which covers both a rejoin and a {@link org.jgroups.MergeView} reuniting a partition whose
    * members the two sides purged while they were apart. A message arriving from the owner is not on its own
    * evidence that it is back - that is exactly the in-flight case the tombstone exists to reject.
    */
   synchronized void admit(String owner) {
      purged.remove(owner);
   }

   synchronized void addLocalOnly(String address, RegistrationInfo registration) {
      localSubs.computeIfAbsent(address, k -> new LinkedHashSet<>()).add(registration);
      rebuild(address);
   }

   synchronized void removeLocalOnly(String address, RegistrationInfo registration) {
      Set<RegistrationInfo> registrations = localSubs.get(address);
      if (registrations != null) {
         registrations.remove(registration);
         if (registrations.isEmpty()) {
            localSubs.remove(address);
         }
      }
      rebuild(address);
   }

   /** Rebuilt from the authoritative slices rather than patched, so the index can never drift out of sync. */
   private void rebuild(String address) {
      List<RegistrationInfo> merged = new ArrayList<>();
      Set<RegistrationInfo> local = localSubs.get(address);
      if (local != null) {
         merged.addAll(local);
      }
      for (Slice slice : slices.values()) {
         Set<RegistrationInfo> registrations = slice.subs.get(address);
         if (registrations != null) {
            merged.addAll(registrations);
         }
      }
      if (merged.isEmpty()) {
         subsIndex.remove(address);
      } else {
         subsIndex.put(address, List.copyOf(merged));
      }
   }
}

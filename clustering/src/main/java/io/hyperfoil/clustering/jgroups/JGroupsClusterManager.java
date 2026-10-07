package io.hyperfoil.clustering.jgroups;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgroups.Address;
import org.jgroups.BytesMessage;
import org.jgroups.JChannel;
import org.jgroups.MergeView;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.GroupRequest;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

import io.hyperfoil.internal.Properties;
import io.vertx.core.Completable;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationListener;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;

/**
 * A Vert.x {@link ClusterManager} implemented directly on JGroups, replacing {@code vertx-infinispan} and the whole
 * Infinispan stack that used to sit between Hyperfoil and the JGroups channel underneath it.
 * <p>
 * Hyperfoil needs only the clustered event bus, node ids and node-left notifications from a cluster manager; the
 * distributed maps, counters and locks that Infinispan brought along were never used. See
 * {@link #getAsyncMap}, {@link #getCounter} and {@link #getLockWithTimeout}, which fail loudly rather than
 * silently degrading to node-local behaviour.
 * <p>
 * The state is replicated as described on {@link ClusterState}. Carrying over the reasoning from the
 * {@code infinispan.xml} this replaces: <em>"We're often killing all agents at once; without replicated state there
 * are high chances that information (e.g. event bus subscriptions) would be lost."</em> This design satisfies that
 * more strongly than the replicated caches did - every node holds the complete state, there is no rebalancing, and
 * cleanup of a departed node is performed independently by every survivor.
 */
public class JGroupsClusterManager implements ClusterManager, RequestHandler, Receiver {
   private static final Logger log = LogManager.getLogger(JGroupsClusterManager.class);

   private static final long STATE_TIMEOUT_MS = 5_000;

   private final String stackFile;
   private final String clusterName;
   private final ClusterState state = new ClusterState();
   /**
    * Version stamp of our own slice. Deliberately a plain field guarded by {@link #writeLock} rather than an
    * atomic: an atomic would read as if allocating a stamp were safe on its own, and it is not - the stamp only
    * means anything if the mutation it labels is applied and sent in that same order.
    */
   private long version;
   private final Map<String, Map<?, ?>> syncMaps = new ConcurrentHashMap<>();
   /**
    * Guards {@link #pendingOps}. Held across slice application and drain so that no update can slip between them.
    */
   private final Object pullLock = new Object();
   /**
    * Serializes the three steps of a mutation of our own slice: stamping it with the next version, applying it
    * here and putting it on the wire. See {@link #mutate}.
    */
   private final Object writeLock = new Object();
   /**
    * Non-null while a state pull is in flight: incremental updates are queued here instead of being applied.
    * <p>
    * A pulled slice at version V is the owner's complete state after V mutations, but the mutations V+1.. travel
    * as multicasts that can overtake the unicast reply. Applying those first would bump our version past V, the
    * slice would then be rejected as stale, and everything the owner registered before V would be lost for good.
    * Queueing until the slice lands keeps the base and the deltas in the right order; the version check then
    * discards the queued updates the slice already contains.
    * <p>
    * Only the pull needs this. A slice that arrives from {@link #announceSlice} shares the multicast channel with
    * its owner's deltas and so is already ordered against them.
    */
   private List<Supplier<Collection<String>>> pendingOps;

   private VertxInternal vertx;
   private ContextInternal context;
   private volatile JChannel channel;
   private volatile MessageDispatcher dispatcher;
   private volatile String nodeId;
   private volatile NodeInfo localNodeInfo;
   private volatile NodeListener nodeListener;
   private volatile RegistrationListener registrationListener;
   private volatile View lastView;

   public JGroupsClusterManager(String stackFile, String clusterName) {
      this.stackFile = stackFile;
      this.clusterName = clusterName;
   }

   /**
    * The connected channel. Only valid once {@link #join} has completed.
    */
   public JChannel channel() {
      return channel;
   }

   @Override
   public void init(Vertx vertx) {
      this.vertx = (VertxInternal) vertx;
      this.context = this.vertx.getOrCreateContext();
   }

   // ------------------------------------------------------------ lifecycle

   @Override
   public void join(Completable<Void> completable) {
      vertx.executeBlocking(() -> {
         JChannel ch = new JChannel(stackFile);
         // The default logical name is hostname-<random int>, which is collision-resistant but not unique;
         // make it explicit so node ids are both readable in logs and effectively unique.
         ch.name(Properties.get(Properties.CLUSTER_NODE_NAME, defaultNodeName()));
         MessageDispatcher d = new MessageDispatcher(ch);
         d.setRequestHandler(this);
         d.setReceiver(this);
         this.channel = ch;
         this.dispatcher = d;
         // Start queueing before connect(): from here on we can receive updates, and none of them may be
         // applied until the state pull below has established the base state.
         beginPull();
         ch.connect(clusterName);
         this.nodeId = ch.getAddress().toString();
         // lastView is deliberately not set here. connect() installs the first view synchronously through
         // viewAccepted, which has already stored it, and by the time this line would run the membership may
         // have moved on again: storing ch.getView() could overwrite the view viewAccepted installed with a
         // later one whose own callback has not run yet. The next diff would then be computed against a view
         // that was never processed, silently losing a departure - no purge, no nodeLeft, no announcement.
         log.info("Joined cluster '{}' as {} with view {}", clusterName, nodeId, ch.getViewAsString());
         return ch;
      }, false).onComplete(result -> {
         if (result.failed()) {
            completable.fail(result.cause());
         } else {
            pullState(completable);
         }
      });
   }

   private static String defaultNodeName() {
      String host;
      try {
         host = InetAddress.getLocalHost().getHostName();
      } catch (Exception e) {
         host = "node";
      }
      return host + "-" + Long.toHexString(ThreadLocalRandom.current().nextLong() >>> 32);
   }

   @Override
   public void leave(Completable<Void> completable) {
      vertx.executeBlocking(() -> {
         MessageDispatcher d = dispatcher;
         JChannel ch = channel;
         dispatcher = null;
         channel = null;
         if (d != null) {
            d.close();
         }
         if (ch != null) {
            ch.disconnect();
            ch.close();
         }
         return null;
      }, false).onComplete(result -> {
         if (result.failed()) {
            log.warn("Error while leaving the cluster", result.cause());
         }
         completable.succeed();
      });
   }

   @Override
   public boolean isActive() {
      JChannel ch = channel;
      return ch != null && ch.isConnected();
   }

   // --------------------------------------------------------- cluster info

   @Override
   public String getNodeId() {
      return nodeId;
   }

   @Override
   public List<String> getNodes() {
      View view = lastView;
      if (view == null) {
         return nodeId == null ? Collections.emptyList() : List.of(nodeId);
      }
      List<String> nodes = new ArrayList<>(view.size());
      for (Address address : view.getMembers()) {
         nodes.add(address.toString());
      }
      return nodes;
   }

   @Override
   public void nodeListener(NodeListener listener) {
      this.nodeListener = listener;
   }

   @Override
   public NodeInfo getNodeInfo() {
      return localNodeInfo;
   }

   @Override
   public void getNodeInfo(String nodeId, Completable<NodeInfo> completable) {
      NodeInfo nodeInfo = state.nodeInfo(nodeId);
      if (nodeInfo == null) {
         completable.fail("Unknown node " + nodeId);
      } else {
         completable.succeed(nodeInfo);
      }
   }

   @Override
   public void setNodeInfo(NodeInfo nodeInfo, Completable<Void> completable) {
      this.localNodeInfo = nodeInfo;
      mutate(v -> {
         state.applyNodeInfo(nodeId, v, nodeInfo);
         return StateMessages.nodeInfo(v, nodeInfo);
      }, Collections.emptyList(), completable);
   }

   // -------------------------------------------------------- registrations

   @Override
   public void registrationListener(RegistrationListener listener) {
      this.registrationListener = listener;
   }

   @Override
   public void addRegistration(String address, RegistrationInfo registration, Completable<Void> completable) {
      if (registration.localOnly()) {
         state.addLocalOnly(address, registration);
         notifyRegistrations(List.of(address));
         completable.succeed();
         return;
      }
      mutate(v -> {
         state.applySubAdd(nodeId, v, address, registration);
         return StateMessages.subscription(StateMessages.OP_SUB_ADD, v, address, registration);
      }, List.of(address), completable);
   }

   @Override
   public void removeRegistration(String address, RegistrationInfo registration, Completable<Void> completable) {
      if (registration.localOnly()) {
         state.removeLocalOnly(address, registration);
         notifyRegistrations(List.of(address));
         completable.succeed();
         return;
      }
      mutate(v -> {
         state.applySubRemove(nodeId, v, address, registration);
         return StateMessages.subscription(StateMessages.OP_SUB_REMOVE, v, address, registration);
      }, List.of(address), completable);
   }

   @Override
   public void getRegistrations(String address, Completable<List<RegistrationInfo>> completable) {
      completable.succeed(state.registrations(address));
   }

   // -------------------------------------------------- unsupported SharedData

   @Override
   public <K, V> void getAsyncMap(String name, Completable<AsyncMap<K, V>> completable) {
      completable.fail(unsupported("AsyncMap"));
   }

   @Override
   public <K, V> Map<K, V> getSyncMap(String name) {
      if ("__vertx.haInfo".equals(name)) {
         log.warn("Vert.x HA requested the '{}' map; {} keeps it node-local, so HA failover will not work.",
               name, getClass().getSimpleName());
      }
      @SuppressWarnings("unchecked")
      Map<K, V> map = (Map<K, V>) syncMaps.computeIfAbsent(name, k -> new ConcurrentHashMap<>());
      return map;
   }

   @Override
   public void getLockWithTimeout(String name, long timeout, Completable<Lock> completable) {
      // JGroups removed LockService and CENTRAL_LOCK in 5.3; a coordinator-RPC lock would need release-on-owner-death,
      // re-election and reentrancy handling, and nothing in Hyperfoil takes a cluster-wide lock.
      completable.fail(unsupported("Lock"));
   }

   @Override
   public void getCounter(String name, Completable<Counter> completable) {
      // Could be backed by org.jgroups.blocks.atomic.CounterService by adding <COUNTER/> to the stack files,
      // but nothing in Hyperfoil uses a cluster-wide counter.
      completable.fail(unsupported("Counter"));
   }

   private UnsupportedOperationException unsupported(String feature) {
      return new UnsupportedOperationException("io.vertx.core.shareddata." + feature + " is not supported by "
            + getClass().getSimpleName() + "; Hyperfoil does not use SharedData");
   }

   // ------------------------------------------------------ JGroups callbacks

   @Override
   public Object handle(Message msg) {
      String sender = msg.getSrc().toString();
      StateMessages.Reader reader = new StateMessages.Reader(payloadOf(msg));
      byte op = reader.op();
      switch (op) {
         case StateMessages.OP_STATE_REQUEST: {
            String id = nodeId;
            if (id == null) {
               // The dispatcher is installed before connect() so that nothing is missed, so a peer can ask for
               // our slice while we are still inside connect() and have not learnt our own address yet. Our
               // slice is empty at that point anyway - we only register consumers once join() has completed -
               // and the asker is repaired by the slice we announce on the view that added us.
               return StateMessages.slice(0, null, Collections.emptyMap());
            }
            return state.encodeSlice(id);
         }
         case StateMessages.OP_NODE_INFO: {
            long v = reader.readLong();
            NodeInfo nodeInfo = reader.readNodeInfo();
            applyOrQueue(() -> {
               state.applyNodeInfo(sender, v, nodeInfo);
               return Collections.emptySet();
            });
            return null;
         }
         case StateMessages.OP_SUB_ADD: {
            long v = reader.readLong();
            String address = reader.readString();
            RegistrationInfo registration = reader.readRegistration();
            applyOrQueue(() -> state.applySubAdd(sender, v, address, registration)
                  ? List.of(address)
                  : Collections.emptySet());
            return null;
         }
         case StateMessages.OP_SUB_REMOVE: {
            long v = reader.readLong();
            String address = reader.readString();
            RegistrationInfo registration = reader.readRegistration();
            applyOrQueue(() -> state.applySubRemove(sender, v, address, registration)
                  ? List.of(address)
                  : Collections.emptySet());
            return null;
         }
         case StateMessages.OP_SLICE:
            // An unsolicited slice from announceSlice, i.e. a peer repairing us; not part of any pull we started.
            // Applied straight away rather than through applyOrQueue: this arrives on the same multicast channel
            // as the sender's own deltas, so nothing of the sender's can overtake it and there is no base to
            // establish first. Queueing it would only delay the repair a joiner's incomplete pull is waiting for.
            notifyRegistrations(applySlice(sender, reader));
            return null;
         default:
            log.warn("Ignoring cluster state message with unknown opcode {} from {}", op, sender);
            return null;
      }
   }

   private static byte[] payloadOf(Message msg) {
      return Arrays.copyOfRange(msg.getArray(), msg.getOffset(), msg.getOffset() + msg.getLength());
   }

   /** Decodes a slice whose opcode byte has already been consumed, and applies it. */
   private Set<String> applySlice(String owner, StateMessages.Reader reader) {
      long v = reader.readLong();
      NodeInfo nodeInfo = reader.readByte() == 1 ? reader.readNodeInfo() : null;
      int addressCount = reader.readInt();
      Map<String, Set<RegistrationInfo>> subs = new HashMap<>();
      for (int i = 0; i < addressCount; ++i) {
         String address = reader.readString();
         int registrationCount = reader.readInt();
         Set<RegistrationInfo> registrations = new LinkedHashSet<>();
         for (int j = 0; j < registrationCount; ++j) {
            registrations.add(reader.readRegistration());
         }
         subs.put(address, registrations);
      }
      return state.applySlice(owner, v, nodeInfo, subs);
   }

   @Override
   public void viewAccepted(View newView) {
      View oldView = lastView;
      lastView = newView;
      if (oldView == null) {
         return;
      }
      Set<String> affected = new HashSet<>();
      for (Address address : View.leftMembers(oldView, newView)) {
         String departed = address.toString();
         log.info("Node {} left the cluster", departed);
         affected.addAll(state.purge(departed));
         NodeListener listener = nodeListener;
         if (listener != null) {
            dispatch(() -> listener.nodeLeft(departed));
         }
      }
      List<Address> arrived = View.newMembers(oldView, newView);
      for (Address address : arrived) {
         String joined = address.toString();
         log.info("Node {} joined the cluster", joined);
         // Lift the tombstone purge left, if there is one: the membership saying a node is back is the only
         // thing that may resurrect it. For a MergeView this is what lets the two halves repair each other
         // with the slices they are about to announce.
         state.admit(joined);
         NodeListener listener = nodeListener;
         if (listener != null) {
            dispatch(() -> listener.nodeAdded(joined));
         }
      }
      notifyRegistrations(affected);
      if (!arrived.isEmpty() || newView instanceof MergeView) {
         log.info("View {} installed, announcing our cluster state slice", newView);
         // Keep sends off the JGroups up-handler thread, as everywhere else in this class.
         dispatch(() -> {
            announceSlice();
            repullState();
         });
      }
   }

   /**
    * Multicasts our own slice so that every member can repair whatever it is missing about us. Done whenever the
    * view gains a member: a joiner has to be told, and a {@link MergeView} reunites nodes that purged each other
    * while they were apart.
    * <p>
    * This is a push rather than only a pull because a push has no response to lose. A pull completes with
    * whatever answered before its deadline, and a peer that was too slow is then simply missing from our state
    * with nothing left to trigger a second attempt - {@link #pullState} can only warn and hope for another view
    * change. The push has the opposite failure mode: the multicast sits in NAKACK2's retransmission table until
    * every member of the view has it. {@link #repullState} runs alongside it to cover the one thing a push
    * cannot: an announcement that reached us before we were ready to accept it.
    * <p>
    * Pushing is also why the receiving side ({@link #handle}, {@code OP_SLICE}) needs no {@link #pendingOps}
    * guard: a slice and the deltas its owner sends afterwards travel the same multicast channel, and NAKACK2
    * delivers one sender's messages in send order, so a delta can never overtake the slice it builds on. That
    * race exists only for the unicast pull responses.
    */
   private void announceSlice() {
      MessageDispatcher d = dispatcher;
      String id = nodeId;
      if (d == null || id == null) {
         return;
      }
      try {
         d.castMessage(null, new BytesMessage(null, state.encodeSlice(id)), RequestOptions.ASYNC());
      } catch (Exception e) {
         // Losing this costs a peer its knowledge of us until the next view change; it must not fail the view.
         log.warn("Could not announce our cluster state slice", e);
      }
   }

   /**
    * The other half of the view-change repair: asks the cluster for the slices we may have just dropped.
    * <p>
    * {@link #announceSlice} alone is not enough once {@link ClusterState#purge} leaves a tombstone. A peer that
    * installs the new view before we install ours announces its slice straight away, and that multicast can
    * reach us while its owner is still tombstoned here, in which case it is discarded - correctly, since from
    * our side the sender is not yet a member. Nothing would carry that peer's state to us afterwards: it has
    * already announced, and it announces again only on the next view change. Pulling after the admits in
    * {@link #viewAccepted} closes the window from the side that knows the admits have happened.
    * <p>
    * Unlike the pull in {@link #join}, this one must not touch {@link #pendingOps}: draining a join's queue here
    * would apply its deltas before the slice they were queued to follow, and {@link ClusterState} would then
    * reject that slice as stale. A join already pulls for itself, so there is nothing to add.
    */
   private void repullState() {
      synchronized (pullLock) {
         if (pendingOps != null) {
            return;
         }
      }
      pullState(null);
   }

   // ----------------------------------------------------------- plumbing

   private void pullState(Completable<Void> completable) {
      MessageDispatcher d = dispatcher;
      JChannel ch = channel;
      if (d == null || ch == null || ch.getView() == null || ch.getView().size() < 2) {
         // Nobody to pull from: whatever we queued is already the whole truth.
         notifyRegistrations(drainPending());
         if (completable != null) {
            completable.succeed();
         }
         return;
      }
      try {
         cast(d, StateMessages.stateRequest(), STATE_TIMEOUT_MS)
               // Hand back to Vert.x before doing anything with the result. This future is completed by a
               // JGroups up-handler thread while the GroupRequest's lock is held, and what runs next is the
               // whole remainder of the clustered bootstrap: the event bus, the Netty server, every verticle
               // Vert.x deploys once join() reports success. None of that belongs on a protocol thread, where
               // it blocks delivery for that sender, and an exception escaping there is captured into the
               // GroupRequest's own future, which nobody reads - join() would simply never complete and the
               // node would hang at startup with nothing in the log to say why.
               .whenComplete((responses, error) -> dispatch(() -> completePull(responses, error, completable)));
      } catch (Exception e) {
         log.warn("Could not request cluster state from peers", e);
         notifyRegistrations(drainPending());
         if (completable != null) {
            completable.succeed();
         }
      }
   }

   /**
    * The second half of {@link #pullState}: applies the slices the peers returned, drains whatever queued
    * behind them and releases the join. Always runs on the Vert.x context, never on a JGroups thread.
    */
   private void completePull(RspList<byte[]> responses, Throwable error, Completable<Void> completable) {
      try {
         if (error != null) {
            // Not fatal, and not worth retrying here: every peer announces its own slice when it sees
            // us appear in the view, and that multicast is retransmitted until it lands. See
            // announceSlice - the pull is the fast path, the announcement is the guarantee.
            log.warn("Could not pull cluster state from all peers; the slice each of them announces on "
                  + "our join will repair whatever is missing", error);
         }
         Set<String> affected = new HashSet<>();
         synchronized (pullLock) {
            if (error == null) {
               for (Map.Entry<Address, Rsp<byte[]>> entry : responses.entrySet()) {
                  Rsp<byte[]> response = entry.getValue();
                  if (!response.wasReceived() || response.getValue() == null) {
                     continue;
                  }
                  StateMessages.Reader reader = new StateMessages.Reader(response.getValue());
                  reader.op(); // OP_SLICE
                  affected.addAll(applySlice(entry.getKey().toString(), reader));
               }
            }
            affected.addAll(endPull());
         }
         notifyRegistrations(affected);
      } catch (RuntimeException e) {
         // A malformed slice must not cost us the join. Leaving pendingOps installed would queue every later
         // update for good, and never completing would hang startup; an incomplete pull is repaired by the
         // slice every peer announces on the view that added us.
         log.error("Failed to apply the cluster state pulled from peers", e);
         notifyRegistrations(drainPending());
      } finally {
         if (completable != null) {
            completable.succeed();
         }
      }
   }

   private void beginPull() {
      synchronized (pullLock) {
         if (pendingOps == null) {
            pendingOps = new ArrayList<>();
         }
      }
   }

   /** Applies whatever accumulated while the pull was in flight and stops queueing. Caller holds {@link #pullLock}. */
   private Set<String> endPull() {
      List<Supplier<Collection<String>>> queued = pendingOps;
      pendingOps = null;
      if (queued == null) {
         return Collections.emptySet();
      }
      Set<String> affected = new HashSet<>();
      for (Supplier<Collection<String>> op : queued) {
         affected.addAll(op.get());
      }
      return affected;
   }

   private Set<String> drainPending() {
      synchronized (pullLock) {
         return endPull();
      }
   }

   private void applyOrQueue(Supplier<Collection<String>> op) {
      synchronized (pullLock) {
         if (pendingOps != null) {
            pendingOps.add(op);
            return;
         }
      }
      notifyRegistrations(op.get());
   }

   /**
    * Multicasts {@code payload} to the whole cluster and returns a future that completes within {@code timeoutMs}
    * with whatever responses arrived.
    * <p>
    * The deadline has to be imposed here rather than left to {@link RequestOptions#timeout(long)}. JGroups reads
    * that value only on the blocking path: {@code Request.execute} returns as soon as the request has been sent
    * when it is not asked to block, so on a future the setting is inert and the request completes only once every
    * member has answered or has left the view. Under sustained packet loss that can be never, and neither caller
    * survives it - a consumer registration would never resolve, and a state pull that never lands leaves
    * {@code pendingOps} installed, so every later update is queued and silently never applied.
    * <p>
    * Timing out through {@code waitForCompletion} rather than cancelling is deliberate: that is the same call the
    * blocking path makes, and it completes the request with the responses received so far and releases the
    * {@code RequestCorrelator} entry. Partial state from the peers that did answer is worth strictly more than
    * none, and passing a zero timeout only asks for what has already arrived - it does not block the timer thread.
    */
   private CompletableFuture<RspList<byte[]>> cast(MessageDispatcher d, byte[] payload, long timeoutMs)
         throws Exception {
      CompletableFuture<RspList<byte[]>> request = d.castMessageWithFuture(null, new BytesMessage(null, payload),
            RequestOptions.SYNC().timeout(timeoutMs));
      if (!(request instanceof GroupRequest<byte[]> groupRequest)) {
         // Should not happen; castMessageWithFuture returns a GroupRequest. Rather than risk an unbounded wait,
         // fall back to failing the future so the callers take their warn-and-continue path.
         return request.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
      }
      long timer = vertx.setTimer(timeoutMs, id -> {
         if (!request.isDone()) {
            log.warn("Cluster RPC did not get every response within {} ms, continuing with those that arrived",
                  timeoutMs);
            groupRequest.waitForCompletion(0, TimeUnit.MILLISECONDS);
         }
      });
      return request.whenComplete((responses, error) -> vertx.cancelTimer(timer));
   }

   /**
    * Stamps a mutation of our own slice with the next version, applies it locally and multicasts it.
    * <p>
    * All three steps are done under {@link #writeLock}, and that is the whole point of this method. The SPI
    * places no constraint on who calls {@link #addRegistration} or {@link #removeRegistration}: Vert.x invokes
    * them inline on whichever thread registered the consumer, so two of them can run at once. Stamping outside
    * the lock lets the mutation stamped {@code v} be applied after the one stamped {@code v+1}, and
    * {@link ClusterState} then drops {@code v} as stale - on <em>this</em> node as well as on every peer, so
    * the registration is gone everywhere and nothing repairs it, because the slice we would announce no longer
    * contains it either. The send belongs inside the lock for the same reason: NAKACK2 preserves the order
    * messages were sent in, not the order their versions were allocated in.
    * <p>
    * The send is asynchronous, as in {@link #announceSlice}. Waiting for responses would be bookkeeping for an
    * answer nobody reads: a missing response means a slow peer rather than a lost update, because NAKACK2 keeps
    * retransmitting the multicast to every member of the view, and a peer that really did miss it - because it
    * was partitioned away - is repaired by the slice announced on the next view change, not by anything this
    * method could do. The lock is still held while the message goes down the stack, which MFC can block on when
    * credits run out; that is acceptable here, these are rare, tiny control messages, one per registration.
    * <p>
    * Failing to hand the message to the transport at all is a different matter and fails {@code completable}.
    * The mutation is in our local state and has consumed a version by then, but no peer will ever see it and
    * nothing is scheduled to retry, so the registration is not cluster-wide and reporting success would leave
    * the caller with a consumer that silently never receives. An absent dispatcher is not a failure: that is a
    * node that has not joined or has already left, where the local state is all there is.
    *
    * @param mutation applies the mutation to {@link #state} and returns the payload announcing it
    * @param affected the addresses whose registrations the mutation changes, for the registration listener
    */
   private void mutate(LongFunction<byte[]> mutation, Collection<String> affected, Completable<Void> completable) {
      Exception failure = null;
      synchronized (writeLock) {
         byte[] payload = mutation.apply(++version);
         MessageDispatcher d = dispatcher;
         if (d != null) {
            try {
               d.castMessage(null, new BytesMessage(null, payload), RequestOptions.ASYNC());
            } catch (Exception e) {
               failure = e;
            }
         }
      }
      notifyRegistrations(affected);
      if (failure != null) {
         log.error("Cannot broadcast cluster state update; this node's registration is not cluster-wide", failure);
         completable.fail(failure);
      } else {
         completable.succeed();
      }
   }

   /**
    * Hands the Vert.x registration listener the current registrations for each address a mutation touched.
    * <p>
    * The snapshot is read inside {@link #dispatch} rather than here. These calls come from JGroups up-handler
    * threads and from whichever thread called {@link #addRegistration}, so several can be in flight for one
    * address at a time; reading on the calling thread lets an older snapshot be delivered after a newer one,
    * which leaves the event bus routing to a stale set of nodes until something else touches that address.
    * Reading inside the context puts every read on the one event loop {@code dispatch} targets, in the order
    * the events are delivered, so the last one delivered is always the last one read.
    */
   private void notifyRegistrations(Collection<String> addresses) {
      RegistrationListener listener = registrationListener;
      if (listener == null || addresses.isEmpty()) {
         return;
      }
      for (String address : addresses) {
         if (!listener.wantsUpdatesFor(address)) {
            continue;
         }
         dispatch(() -> listener
               .registrationsUpdated(new RegistrationUpdateEvent(address, state.registrations(address))));
      }
   }

   /**
    * Single choke point for handing control back to Vert.x. Listener callbacks must never run on a JGroups
    * up-handler thread: {@code ControllerVerticle.nodeLeft} walks runs and fails agents.
    */
   private void dispatch(Runnable action) {
      ContextInternal ctx = context;
      if (ctx == null) {
         action.run();
      } else {
         ctx.runOnContext(v -> action.run());
      }
   }
}

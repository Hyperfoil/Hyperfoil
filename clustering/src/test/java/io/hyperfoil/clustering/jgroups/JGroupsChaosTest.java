package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jgroups.protocols.DELAY;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.DUPL;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.SHUFFLE;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * Chaos tests: every node registers consumers continuously while the network underneath misbehaves, and once the
 * fault is healed all nodes must agree on exactly the same set of registrations.
 * <p>
 * The shape is borrowed from Chaos Mesh suites - drive a steady workload, inject a fault half way through, keep
 * driving, heal, then verify the whole final state rather than just that nothing threw. The faults themselves are
 * JGroups protocols spliced into the stack instead of cluster-level chaos, which is both far cheaper and a closer
 * fit: {@code DELAY}, {@code DISCARD}, {@code DUPL} and {@code SHUFFLE} correspond to Chaos Mesh's network delay,
 * loss, duplicate and reorder actions, and they hit the transport directly rather than hoping a pod-level fault
 * happens to perturb it.
 * <p>
 * This is the end-to-end counterpart to {@link ClusterStateTest}: that pins the merge rules on their own, while
 * these drive real channels and so also cover the pieces those rules depend on - the per-owner version stamps,
 * the retransmission the cluster manager assumes underneath, and the state pull on join.
 * <p>
 * We intentionally insert network faults (like delays or lost packets) <b>below</b> the reliable
 * network protocols (NAKACK2/UNICAST3), not above them. Here is why:
 * <ul>
 * <li><b>We rely on message order:</b> {@code mutate()} and {@code announceSlice()} expect
 * messages from the same sender to arrive in the exact order they were sent. If an older message
 * arrives late, {@link ClusterState} will drop it permanently.</li>
 * <li><b>Real-world network behavior:</b> On a real network, messages get scrambled during transit.
 * However, NAKACK2 fixes this order and removes duplicates <i>before</i> the messages reach our application.</li>
 * <li><b>Testing realistic chaos:</b> By putting faults below NAKACK2, we test what happens
 * on a real network. We prove that the system stays stable even if the raw network loses,
 * duplicates, or reorders messages.</li>
 * <li><b>Breaking the rules:</b> If we put faults <i>above</i> NAKACK2, the tests would fail.
 * This is not a bug in our code. It fails because it breaks the strict message order that our
 * design requires.</li>
 * </ul>
 * <p>
 * Because NAKACK2 already prevents out-of-order and duplicate messages, these chaos tests
 * do not test the version check inside {@link ClusterState}.
 * <p>
 * We still need that version check for a different problem: when a full state pull happens at the
 * exact same time as a small data update, and they travel on different network channels. We test
 * those specific timing issues in {@link AnnounceSliceRaceTest}, {@link RepullStateRaceTest},
 * and {@link JoinPullRaceTest} instead.
 * <p>
 * Node departure and full partitions are covered separately, in {@link JGroupsClusterManagerTest} and
 * {@link JGroupsClusterManagerPartitionTest}.
 */
public class JGroupsChaosTest extends ClusterManagerTestBase {
   private static final String FEED = "chaos-feed";
   private static final int NODES = 3;
   private static final int REGISTRATIONS_PER_NODE = 12;

   /** A fault that can be injected into a live cluster and then healed. */
   private interface Fault {
      void inject(List<JGroupsClusterManager> cluster) throws Exception;

      void heal(List<JGroupsClusterManager> cluster);
   }

   static Stream<Arguments> faults() {
      return Stream.of(
            // Simulates a slow network with high latency.
            // Every message takes longer to arrive.
            Arguments.of("delay", protocolFault(() -> {
               DELAY delay = new DELAY();
               delay.setInDelay(3);
               delay.setOutDelay(3);
               return delay;
            })),

            // Simulates lost network packets.
            // The network layer (NAKACK2 and UNICAST3) must automatically resend missing messages.
            Arguments.of("loss", protocolFault(() -> new DISCARD().setUpDiscardRate(0.2))),

            // Simulates receiving the same message multiple times.
            // The network layer must remove the duplicates before our application sees them.
            Arguments.of("duplicate", protocolFault(() -> new DUPL(true, true, 3, 3))),

            // Simulates messages arriving in the wrong order.
            // The network layer must fix the order before our application sees them.
            // We set 'maxTime' to 100ms so the test does not get stuck waiting
            // for the SHUFFLE buffer to fill up.
            Arguments.of("reorder", protocolFault(
                  () -> new SHUFFLE().setUp(true).setDown(true).setMaxSize(4).setMaxTime(100))));
   }

   @ParameterizedTest(name = "converges under {0}")
   @MethodSource("faults")
   public void shouldConvergeUnder(String name, Fault fault) throws Exception {
      List<JGroupsClusterManager> cluster = new ArrayList<>();
      for (int i = 0; i < NODES; i++) {
         cluster.add(join("chaos-" + name));
      }
      for (JGroupsClusterManager manager : cluster) {
         awaitTrue("every node sees the full cluster", () -> manager.getNodes().size() == NODES);
      }

      Set<RegistrationInfo> expected = new LinkedHashSet<>();
      for (JGroupsClusterManager manager : cluster) {
         for (int seq = 1; seq <= REGISTRATIONS_PER_NODE; seq++) {
            expected.add(new RegistrationInfo(manager.getNodeId(), seq, false));
         }
      }

      int total = NODES * REGISTRATIONS_PER_NODE;
      int half = REGISTRATIONS_PER_NODE / 2;
      AtomicInteger done = new AtomicInteger();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      CountDownLatch firstHalf = new CountDownLatch(NODES);
      CountDownLatch faultInjected = new CountDownLatch(1);
      CountDownLatch finished = new CountDownLatch(NODES);

      // One writer per node, all registering at the same time. This is the between-node concurrency: each node
      // is the sole writer of its own slice, so what the fault gets to perturb is three independent writers
      // whose updates cross on the wire. Several threads writing the *same* slice is a different hazard and is
      // covered on a quiet network by ConcurrentRegistrationTest. Writers stop at the half way mark and wait
      // for the fault rather than racing it - on SHARED_LOOPBACK the whole workload otherwise finishes before
      // any fault could be injected, and the test would quietly assert nothing.
      for (JGroupsClusterManager manager : cluster) {
         Thread writer = new Thread(() -> {
            try {
               for (int seq = 1; seq <= REGISTRATIONS_PER_NODE; seq++) {
                  if (seq == half + 1) {
                     firstHalf.countDown();
                     if (!faultInjected.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
                        throw new AssertionError("fault was never injected");
                     }
                  }
                  addRegistration(manager, FEED, new RegistrationInfo(manager.getNodeId(), seq, false));
                  done.incrementAndGet();
               }
            } catch (Throwable t) {
               failure.compareAndSet(null, t);
            } finally {
               firstHalf.countDown();
               finished.countDown();
            }
         }, "chaos-writer-" + manager.getNodeId());
         writer.setDaemon(true);
         writer.start();
      }

      // Inject half way through, so the fault straddles registrations that are already replicated and ones
      // that are not. Injecting before the workload starts would only ever test the empty-state case.
      if (!firstHalf.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
         fail("workload stalled before the fault; completed " + done.get() + " of " + total);
      }
      int beforeFault = done.get();
      long injectedAt = System.nanoTime();
      fault.inject(cluster);
      faultInjected.countDown();
      try {
         if (!finished.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
            fail("writers did not finish under " + name + "; completed " + done.get() + " of " + total);
         }
      } finally {
         fault.heal(cluster);
      }
      if (failure.get() != null) {
         throw new AssertionError("A writer failed under " + name, failure.get());
      }
      // Guards against the fault being a no-op: if nothing was registered while it was active, a broken
      // injection would look identical to a clean run.
      assertTrue(done.get() - beforeFault >= total / 2,
            "expected at least half the workload to run under " + name + ", only "
                  + (done.get() - beforeFault) + " of " + total + " did");
      System.out.printf("[chaos] %s: %d/%d registrations under fault, %d ms%n",
            name, done.get() - beforeFault, total, (System.nanoTime() - injectedAt) / 1_000_000);

      for (JGroupsClusterManager manager : cluster) {
         assertRegistrations("converged at " + manager.getNodeId() + " under " + name, manager, FEED, expected);
         // Set equality alone would hide a duplicate, which is exactly what a replayed update produces.
         assertEquals(expected.size(), registrations(manager, FEED).size(),
               "duplicate registrations at " + manager.getNodeId() + " under " + name);
      }
   }

   /**
    * In the standard {@link #shouldConvergeUnder} tests, all nodes join the cluster before anyone starts writing data.
    * Because of this, a new node joining never overlaps with live data updates.
    * This means the {@code pendingOps} queue is never used in those tests.
    * <p>
    * This test is different. It makes a new node join <i>while</i> other nodes are actively writing data.
    * <p>
    * We use the {@code DELAY} fault here on purpose. It makes the network slow, which stretches the time
    * it takes for the new node to download the full state. This creates a longer window of time for small
    * data updates (deltas) to arrive during the download, forcing the system to use the {@code pendingOps} queue.
    * <p>
    * Other tests (like {@link AnnounceSliceRaceTest}, {@link RepullStateRaceTest}, and {@link JoinPullRaceTest})
    * test this exact timing by artificially pausing the code. This test proves the queue works in a
    * real-world scenario, with actual network delays and real threads writing at the same time.
    */
   @Test
   public void shouldConvergeWhenNodeJoinsMidWorkloadUnderDelay() throws Exception {
      String cluster = "chaos-late-join";
      List<JGroupsClusterManager> writers = new ArrayList<>();
      for (int i = 0; i < NODES; i++) {
         writers.add(join(cluster));
      }

      Set<RegistrationInfo> expected = new LinkedHashSet<>();
      for (JGroupsClusterManager manager : writers) {
         for (int seq = 1; seq <= REGISTRATIONS_PER_NODE; seq++) {
            expected.add(new RegistrationInfo(manager.getNodeId(), seq, false));
         }
      }

      int half = REGISTRATIONS_PER_NODE / 2;
      AtomicReference<Throwable> failure = new AtomicReference<>();
      CountDownLatch firstHalf = new CountDownLatch(NODES);
      CountDownLatch faultInjected = new CountDownLatch(1);
      CountDownLatch finished = new CountDownLatch(NODES);

      for (JGroupsClusterManager manager : writers) {
         Thread writer = new Thread(() -> {
            try {
               for (int seq = 1; seq <= REGISTRATIONS_PER_NODE; seq++) {
                  if (seq == half + 1) {
                     firstHalf.countDown();
                     if (!faultInjected.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
                        throw new AssertionError("fault was never injected");
                     }
                  }
                  addRegistration(manager, FEED, new RegistrationInfo(manager.getNodeId(), seq, false));
               }
            } catch (Throwable t) {
               failure.compareAndSet(null, t);
            } finally {
               firstHalf.countDown();
               finished.countDown();
            }
         }, "chaos-late-join-writer-" + manager.getNodeId());
         writer.setDaemon(true);
         writer.start();
      }

      if (!firstHalf.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
         fail("workload stalled before the fault");
      }
      Fault fault = protocolFault(() -> {
         DELAY delay = new DELAY();
         delay.setInDelay(3);
         delay.setOutDelay(3);
         return delay;
      });
      fault.inject(writers);
      faultInjected.countDown();

      // Join while the others are still writing under the fault, so this node's own state pull overlaps live
      // deltas from its peers.
      JGroupsClusterManager joiner = join(cluster);

      try {
         if (!finished.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
            fail("writers did not finish");
         }
      } finally {
         fault.heal(writers);
      }
      if (failure.get() != null) {
         throw new AssertionError("A writer failed", failure.get());
      }

      List<JGroupsClusterManager> all = new ArrayList<>(writers);
      all.add(joiner);
      for (JGroupsClusterManager manager : all) {
         assertRegistrations("converged at " + manager.getNodeId() + " after a mid-workload join", manager, FEED,
               expected);
         assertEquals(expected.size(), registrations(manager, FEED).size(),
               "duplicate registrations at " + manager.getNodeId() + " after a mid-workload join");
      }
   }

   /**
    * Inserts a fault protocol into the network stack of every node.
    * <p>
    * It places this protocol at the very bottom, directly above the raw network transport.
    * This allows it to see messages exactly as they enter or leave the network.
    * <p>
    * Important: We must create a new protocol object for every node. A single
    * protocol instance cannot be shared between multiple nodes.
    */
   private static Fault protocolFault(Supplier<Protocol> factory) {
      return new Fault() {
         private final List<Protocol> injected = new ArrayList<>();

         @Override
         public void inject(List<JGroupsClusterManager> cluster) throws Exception {
            for (JGroupsClusterManager manager : cluster) {
               Protocol protocol = factory.get();
               manager.channel().getProtocolStack()
                     .insertProtocol(protocol, ProtocolStack.Position.ABOVE, SHARED_LOOPBACK.class);
               // insertProtocol only rewires the up/down pointers - it runs no lifecycle. Skipping this is
               // silent rather than noisy: DELAY.down() parks messages on a queue that its init() thread is
               // supposed to drain, so without init() every outgoing message simply disappears.
               protocol.init();
               protocol.start();
               injected.add(protocol);
            }
         }

         @Override
         public void heal(List<JGroupsClusterManager> cluster) {
            // The SHUFFLE protocol holds messages for a short time before sending them.
            // Calling flush(true) forces it to send all waiting messages immediately and stops the shuffling.
            // Because of this, we do not need to wait for it to finish naturally.
            for (Protocol protocol : injected) {
               if (protocol instanceof SHUFFLE shuffle) {
                  shuffle.flush(true);
               }
            }
            // Some protocols (like DELAY) use a background thread to deliver messages.
            // We must pause for a short time (200ms) to let these threads finish their work.
            // If we remove the protocol while it is still trying to send a message, it breaks
            // the network links and causes errors in the log.
            // (The system would safely resend the lost message anyway, but we want to avoid messy log errors).
            try {
               Thread.sleep(200);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
            for (int i = 0; i < cluster.size(); i++) {
               Protocol protocol = injected.get(i);
               protocol.stop();
               protocol.destroy();
               cluster.get(i).channel().getProtocolStack().removeProtocol(protocol);
            }
         }
      };
   }
}

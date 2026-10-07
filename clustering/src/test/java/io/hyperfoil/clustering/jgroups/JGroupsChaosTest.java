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
            // Chaos Mesh "network delay": every hop is slowed, so responses to a state pull can arrive after the
            // multicasts that logically follow them. This is the reordering window the pending-op queue exists for.
            Arguments.of("delay", protocolFault(() -> {
               DELAY delay = new DELAY();
               delay.setInDelay(3);
               delay.setOutDelay(3);
               return delay;
            })),
            // Chaos Mesh "network loss": NAKACK2 and UNICAST3 have to retransmit everything that goes missing.
            Arguments.of("loss", protocolFault(() -> new DISCARD().setUpDiscardRate(0.2))),
            // Chaos Mesh "network duplicate": every update is delivered several times. The version stamps must
            // make the replays no-ops rather than producing duplicate registrations.
            Arguments.of("duplicate", protocolFault(() -> new DUPL(true, true, 3, 3))),
            // Reordering: updates arrive out of the order they were sent, so a stale update can land after a
            // newer one. Nothing but the version check stops it from overwriting.
            // max_time is what flushes a buffer that never fills; the 1500ms default would hold the tail of
            // every burst long enough to dominate the run.
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
    * Splices a protocol directly above the transport on every node, so it sees messages exactly as they hit and
    * leave the wire. Each node needs its own instance: a protocol belongs to one stack.
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
            for (int i = 0; i < cluster.size(); i++) {
               Protocol protocol = injected.get(i);
               cluster.get(i).channel().getProtocolStack().removeProtocol(protocol);
               protocol.stop();
               // DELAY's handler thread only dies on destroy(), and it spins rather than blocking.
               protocol.destroy();
            }
         }
      };
   }
}

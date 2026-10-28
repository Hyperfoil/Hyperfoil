package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.Test;

import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * Consumers registered concurrently on one node must all survive, locally and on its peers.
 * <p>
 * Nothing in the SPI serializes this. {@code ClusteredEventBus.onLocalRegistration} calls
 * {@link io.vertx.core.spi.cluster.ClusterManager#addRegistration addRegistration} inline on whichever thread
 * deployed the consumer, and a verticle deployed with an instance count registers its consumers on that many
 * event loops at once, so the cluster manager has to allocate its version stamps, apply them and put them on
 * the wire under its own lock.
 * <p>
 * The failure this pins down is silent and unrecoverable rather than transient. Allocating version {@code v}
 * and applying it as two steps lets the mutation stamped {@code v} be applied after the one stamped
 * {@code v+1}; {@link ClusterState} then rejects {@code v} as stale on the writer itself as well as on every
 * peer, so the registration exists nowhere - and the slice the owner announces on the next view change cannot
 * repair it, because that slice no longer contains it either. The event bus is then left with a consumer that
 * no sender can address for the lifetime of the node. Against the three-step version of this code the
 * assertion below reported 786 of 1600 registrations surviving.
 * <p>
 * The second node is not decoration: it is what covers the send happening under the same lock. NAKACK2
 * preserves the order messages were sent in, not the order their versions were allocated in, so stamping
 * under a lock and then sending outside it loses registrations on the peers while the writer itself looks
 * perfectly healthy.
 */
public class ConcurrentRegistrationTest extends ClusterManagerTestBase {
   private static final String ADDRESS = "feed";
   private static final int THREADS = 8;
   private static final int PER_THREAD = 200;

   @Test
   public void concurrentRegistrationsMustAllSurvive() throws Exception {
      String cluster = "concurrent-registration";
      JGroupsClusterManager writer = join(cluster);
      JGroupsClusterManager observer = join(cluster);

      Set<RegistrationInfo> expected = new LinkedHashSet<>();
      List<Thread> workers = new ArrayList<>();
      // Start them together so the registrations really do overlap rather than queueing up behind each other
      CyclicBarrier barrier = new CyclicBarrier(THREADS);
      for (int t = 0; t < THREADS; ++t) {
         int base = t * PER_THREAD;
         for (int i = 0; i < PER_THREAD; ++i) {
            expected.add(new RegistrationInfo(writer.getNodeId(), base + i, false));
         }
         Thread worker = new Thread(() -> {
            try {
               barrier.await();
            } catch (Exception e) {
               throw new AssertionError(e);
            }
            for (int i = 0; i < PER_THREAD; ++i) {
               addRegistration(writer, ADDRESS, new RegistrationInfo(writer.getNodeId(), base + i, false));
            }
         }, "registrar-" + t);
         worker.start();
         workers.add(worker);
      }
      for (Thread worker : workers) {
         worker.join();
      }

      // addRegistration only completes once the multicast has been answered, so the writer is already final
      assertEquals(expected, Set.copyOf(registrations(writer, ADDRESS)), "registrations lost at the writer");
      assertRegistrations("every registration at the observer", observer, ADDRESS, expected);
   }
}

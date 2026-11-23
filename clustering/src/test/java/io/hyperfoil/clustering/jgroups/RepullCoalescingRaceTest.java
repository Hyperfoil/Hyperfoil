package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.stack.ProtocolStack;
import org.junit.jupiter.api.Test;

import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * Tests that a new {@code repullState()} request is not ignored if another pull is already running.
 * If we ignore it, we might miss data from nodes that joined or recovered after the first pull started.
 * <p>
 * <b>How this test works:</b>
 * <ul>
 * <li>Node "peer" loses Node "X"'s data.</li>
 * <li>We block Node X from sending messages (simulating network issues).</li>
 * <li>The peer starts its first pull, but Node X cannot answer. We pause the peer before this pull finishes.</li>
 * <li>While paused, we request a second pull. (In a real system, a cluster view change triggers this).</li>
 * <li>We unblock Node X so it can send messages again, and resume the peer's first pull.</li>
 * </ul>
 * <p>
 * <b>The result:</b>
 * The peer finishes the first pull and immediately
 * starts the second pull. The second pull reaches the now-active Node X and recovers the data.
 */
public class RepullCoalescingRaceTest extends ClusterManagerTestBase {
   private static final String FEED = "repull-coalescing-feed";

   @Test
   public void secondRepullMustNotBeDroppedWhileFirstIsInFlight() throws Exception {
      CountDownLatch firstPullPaused = new CountDownLatch(1);
      CountDownLatch resumeFirstPull = new CountDownLatch(1);

      // Only pause the manual pulls we trigger in this test, not the automatic pull on join.
      AtomicBoolean pauseEnabled = new AtomicBoolean(false);
      String cluster = "repull-coalescing";

      JGroupsClusterManager x = join(cluster);
      JGroupsClusterManager peer = join(cluster, null, () -> {
         if (!pauseEnabled.get()) {
            return;
         }
         // Pause right after the missing response for X comes back, but before it is saved.
         firstPullPaused.countDown();
         try {
            if (!resumeFirstPull.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
               throw new AssertionError("Test timed out waiting to resume the first pull");
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
         }
      });

      // Step 1: Add data to Node X. The peer needs to recover this data later.
      Set<RegistrationInfo> xData = new LinkedHashSet<>();
      for (int seq = 1; seq <= 5; seq++) {
         RegistrationInfo reg = new RegistrationInfo(x.getNodeId(), seq, false);
         addRegistration(x, FEED, reg);
         xData.add(reg);
      }
      assertRegistrations("Peer has X's initial state", peer, FEED, xData);

      // Step 2: Manually delete Node X's data on the peer, forcing it to recover it later.
      ClusterState peerState = peer.state();
      peerState.purge(x.getNodeId());
      peerState.admit(x.getNodeId());
      assertEquals(Set.of(), Set.copyOf(registrations(peer, FEED)), "Peer dropped X's state due to tombstone");

      // Step 3: Block Node X's messages so the peer's first pull won't receive X's data.
      // This simulates an unresponsive node in production.
      // We block messages above UNICAST3 so the cluster doesn't think Node X crashed
      // (a crash would trigger an unwanted view change and hide the bug we are testing).
      DISCARD discard = new DISCARD().discardAll(true);
      x.channel().getProtocolStack().insertProtocol(discard, ProtocolStack.Position.ABOVE, UNICAST3.class);
      discard.init();
      discard.start();

      // Step 4: The peer starts the first pull. It pauses before saving the downloaded data.
      pauseEnabled.set(true);
      invoke(JGroupsClusterManager.class, peer, "repullState", new Class<?>[0]);
      if (!firstPullPaused.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
         throw new AssertionError("first repullState never paused");
      }

      // Step 5: While the first pull is still running, we request a second pull.
      // In production, a cluster change triggers this.
      invoke(JGroupsClusterManager.class, peer, "repullState", new Class<?>[0]);

      // Step 6: Unblock Node X so the second pull can reach it.
      discard.discardAll(false);

      // Step 7: Resume the first pull.
      resumeFirstPull.countDown();

      // Step 8: Verify the peer successfully recovered X's data through the second pull.
      assertRegistrations("Peer must recover X's data through the second pull", peer, FEED, xData);
   }
}

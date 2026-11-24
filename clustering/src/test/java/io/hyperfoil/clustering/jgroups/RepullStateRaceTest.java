package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * This test reproduces a timing problem (race condition) between {@code repullState()}
 * and a data update ({@code mutate()}).
 * <p>
 * <b>The Problem:</b>
 * <ul>
 * <li>Node A sends its full data (Version 1) to Node B.</li>
 * <li>Right after, Node A makes a small change and sends an update (Version 2).</li>
 * <li>The small update (Version 2) travels faster and arrives at Node B first.</li>
 * </ul>
 * If Node B saves Version 2 first, it will think Version 1 is "old" data and reject it.
 * Node B will permanently lose all the initial data from Version 1.
 * <p>
 * <b>The Solution:</b>
 * The {@code pendingOps} queue holds the small update safely until the full data is downloaded and saved.
 * <p>
 * <b>How this test works:</b>
 * We do not create a real network failure. Instead, we manually delete Node A's data on Node B.
 * This forces Node B to download the data again using {@code repullState()}.
 * We then pause Node B right before it saves the downloaded data to guarantee the small update arrives first.
 */
public class RepullStateRaceTest extends ClusterManagerTestBase {
   private static final String FEED = "repull-race-feed";

   @Test
   public void peerMustNotLoseDataWhenRepullRacesWithMutate() throws Exception {
      CountDownLatch pullPaused = new CountDownLatch(1);
      CountDownLatch resumePull = new CountDownLatch(1);

      // We only want to pause our manual pull later in the test,
      // not the automatic pull that happens when the node first joins.
      AtomicBoolean pauseEnabled = new AtomicBoolean(false);
      String cluster = "repull-race";

      JGroupsClusterManager owner = join(cluster);
      JGroupsClusterManager peer = join(cluster, null, () -> {
         if (!pauseEnabled.get()) {
            return;
         }
         // Pause right after the data finishes downloading, but before it is saved.
         pullPaused.countDown();
         try {
            if (!resumePull.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
               throw new AssertionError("Test timed out waiting to resume the pull");
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
         }
      });

      // Step 1: Add initial data to the owner (Version 1).
      Set<RegistrationInfo> initialData = new LinkedHashSet<>();
      for (int seq = 1; seq <= 5; seq++) {
         RegistrationInfo reg = new RegistrationInfo(owner.getNodeId(), seq, false);
         addRegistration(owner, FEED, reg);
         initialData.add(reg);
      }
      assertRegistrations("Peer has the owner's initial state (Version 1)", peer, FEED, initialData);

      // Step 2: Manually delete the owner's data on the peer.
      // The peer now has to download it again to recover the lost data.
      ClusterState peerState = peer.state();
      peerState.purge(owner.getNodeId());
      peerState.admit(owner.getNodeId());
      assertEquals(Set.of(), Set.copyOf(registrations(peer, FEED)), "Peer dropped owner's state due to tombstone");

      // Step 3: Tell the peer to pull the data again.
      // It downloads Version 1, and then PAUSES before saving it.
      pauseEnabled.set(true);
      invoke(JGroupsClusterManager.class, peer, "repullState", new Class<?>[0]);

      if (!pullPaused.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
         throw new AssertionError("repullState never paused");
      }

      // Step 4: While the peer is paused, the owner adds new data and sends Version 2.
      RegistrationInfo newerData = new RegistrationInfo(owner.getNodeId(), 6, false);
      addRegistration(owner, FEED, newerData);

      // Wait a moment so Version 2 reaches the peer.
      // Because of pendingOps, the peer puts Version 2 in a waiting queue.
      // Without pendingOps, the peer would save Version 2 immediately and cause a bug.
      Thread.sleep(300);

      // Step 5: Resume the peer. It will save Version 1 first, and then process Version 2 from the queue.
      resumePull.countDown();

      // Step 6: Verify the results. If pendingOps was missing, the peer would have rejected
      // Version 1 and lost entries 1 to 5.
      Set<RegistrationInfo> expectedData = new LinkedHashSet<>(initialData);
      expectedData.add(newerData);
      assertRegistrations("Peer must have all data, proving repullState queued the concurrent delta", peer, FEED,
            expectedData);
   }
}
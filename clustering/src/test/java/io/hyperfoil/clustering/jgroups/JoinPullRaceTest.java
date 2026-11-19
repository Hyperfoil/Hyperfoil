package io.hyperfoil.clustering.jgroups;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.vertx.core.Promise;
import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * This test proves why the {@code pendingOps} queue in {@code join()} is strictly necessary.
 * (See the field Javadoc on {@code JGroupsClusterManager.pendingOps}).
 * <p>
 * <b>The Problem (Race Condition):</b>
 * <ul>
 * <li>Node A is already in the cluster and has saved data.</li>
 * <li>Node B joins and starts downloading Node A's data (a state pull).</li>
 * <li>While Node B is still downloading, Node A saves new data and sends a small update (a delta).</li>
 * <li>This small update can travel faster and arrive at Node B <i>before</i> the download finishes.</li>
 * </ul>
 * If Node B saves this small update first, it will think the main downloaded data is "old" and reject it.
 * Node B would then permanently lose all of Node A's previous data.
 * <p>
 * <b>The Solution:</b>
 * The {@code pendingOps} queue holds these small updates safely until the large download finishes.
 * <p>
 * <b>How this test works:</b>
 * Normally, when a new node joins, older nodes automatically send their full data. We disable that
 * automatic send here to force the new node to use the manual state pull mechanism. Then, we manually pause
 * the new node right before it finishes the download to guarantee the small update arrives first.
 */
public class JoinPullRaceTest extends ClusterManagerTestBase {
   private static final String FEED = "join-pull-race-feed";

   @Test
   public void joinerMustNotLoseDataWhenPullRacesWithMutate() throws Exception {
      CountDownLatch pullPaused = new CountDownLatch(1);
      CountDownLatch resumePull = new CountDownLatch(1);
      String cluster = "join-pull-race";

      // Step 1: Node A (owner) already has data before Node B joins.
      // We configure it to fail when it tries to send its full data automatically.
      // This forces Node B to download the data manually using a state pull.
      JGroupsClusterManager owner = join(cluster, () -> {
         throw new RuntimeException("simulated loss of the owner's view-triggered announcement");
      });
      Set<RegistrationInfo> initialData = new LinkedHashSet<>();
      for (int seq = 1; seq <= 5; seq++) {
         RegistrationInfo reg = new RegistrationInfo(owner.getNodeId(), seq, false);
         addRegistration(owner, FEED, reg);
         initialData.add(reg);
      }

      // Step 2: Start Node B (joiner) manually so our test does not get stuck waiting.
      // Node B will pull Node A's data and then PAUSE right before saving it.
      JGroupsClusterManager joiner = newManager(cluster, null, () -> {
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
      Promise<Void> joinPromise = Promise.promise();
      joiner.join(joinPromise);

      if (!pullPaused.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
         throw new AssertionError("join() never paused before applying the pulled state");
      }

      // Step 3: While Node B is paused, Node A adds a new entry and sends a small update.
      RegistrationInfo newerData = new RegistrationInfo(owner.getNodeId(), 6, false);
      addRegistration(owner, FEED, newerData);

      // Wait a moment so the small update reaches Node B.
      // Because of pendingOps, Node B will hold this update in a queue.
      // Without pendingOps, Node B would save it immediately and cause a bug.
      Thread.sleep(300);

      // Step 4: Resume Node B. It will now save the downloaded data first,
      // and then process the small updates from the queue.
      resumePull.countDown();
      await(joinPromise.future());

      // Step 5: Verify the results. If pendingOps was broken, Node B would have rejected
      // the downloaded data and lost entries 1 to 5.
      Set<RegistrationInfo> expectedData = new LinkedHashSet<>(initialData);
      expectedData.add(newerData);
      assertRegistrations("Joiner must have all data, proving pendingOps queued the concurrent delta", joiner, FEED,
            expectedData);
   }
}

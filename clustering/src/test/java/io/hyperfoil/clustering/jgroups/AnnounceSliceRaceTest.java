package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * Reproduces the race condition between {@code JGroupsClusterManager.announceSlice()}
 * and {@code mutate()}.
 * <p>
 * <b>The Race Condition:</b> {@code announceSlice()} must hold the {@code writeLock}
 * when reading and sending the state. Without this lock, a concurrent {@code mutate()}
 * could create and send a newer state (V+1) before the older state (V) is sent.
 * If this happens, a peer receives V+1 first, rejects V as older, and loses data.
 * <p>
 * <b>The Scenario:</b> Normal node joins do not trigger this race condition because
 * the joining node requests the state directly. The race only affects a node reconnecting
 * after a network partition (a "merge"). In a merge, the peer relies completely on the
 * {@code announceSlice()} message.
 * <p>
 * Instead of creating a complex network partition, this test simulates it manually using
 * {@link ClusterState#purge} and {@code admit}. This allows us to focus strictly on
 * testing the message order.
 * <p>
 * <b>How it works:</b> To force the exact timing of the race condition, the test uses
 * the {@code afterSliceEncoded} method. This pauses the thread right after the state
 * is read, but before it is sent, allowing the concurrent {@code mutate()} to happen.
 */
public class AnnounceSliceRaceTest extends ClusterManagerTestBase {
   private static final String FEED = "announce-race-feed";

   @Test
   public void peerMustNotLoseDataWhenAnnounceSliceRacesWithMutate() throws Exception {
      CountDownLatch announcePaused = new CountDownLatch(1);
      CountDownLatch resumeAnnounce = new CountDownLatch(1);

      // We only want to pause the specific announceSlice() call we trigger manually.
      // This boolean prevents pausing the automatic announceSlice() during the initial join.
      AtomicBoolean pauseEnabled = new AtomicBoolean(false);
      String cluster = "announce-race";

      JGroupsClusterManager owner = join(cluster, () -> {
         if (!pauseEnabled.get()) {
            return;
         }
         // Pause the thread right after reading state (Version V), but before sending it
         announcePaused.countDown();
         try {
            if (!resumeAnnounce.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
               throw new AssertionError("Test timed out waiting to resume announceSlice");
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
         }
      });
      JGroupsClusterManager peer = join(cluster);

      // Step 1: Add initial state (Version V)
      Set<RegistrationInfo> initialData = new LinkedHashSet<>();
      for (int seq = 1; seq <= 5; seq++) {
         RegistrationInfo reg = new RegistrationInfo(owner.getNodeId(), seq, false);
         addRegistration(owner, FEED, reg);
         initialData.add(reg);
      }
      assertRegistrations("Peer has the owner's initial state (Version V)", peer, FEED, initialData);

      // Step 2: Simulate a network partition and reconnect (merge)
      // The peer clears the owner's state. It now depends entirely on announceSlice() to get it back.
      Object peerState = stateOf(peer);
      invoke(ClusterState.class, peerState, "purge", new Class<?>[] { String.class }, owner.getNodeId());
      invoke(ClusterState.class, peerState, "admit", new Class<?>[] { String.class }, owner.getNodeId());
      assertEquals(Set.of(), Set.copyOf(registrations(peer, FEED)), "Peer dropped owner's state due to partition");

      // Enable our hook to pause the announceSlice() method
      pauseEnabled.set(true);

      // Step 3: Start announceSlice() in a background thread.
      // It will read the older state (Version V) and then PAUSE.
      Method announceSlice = JGroupsClusterManager.class.getDeclaredMethod("announceSlice");
      announceSlice.setAccessible(true);
      Thread announcerThread = new Thread(() -> {
         try {
            announceSlice.invoke(owner);
         } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
         }
      }, "announcer-thread");
      announcerThread.start();

      // Wait until the announcer thread actually reads Version V and pauses
      if (!announcePaused.await(AWAIT_MS, TimeUnit.MILLISECONDS)) {
         throw new AssertionError("announceSlice never paused");
      }

      // Step 4: While announce is paused, create and send the newer state (Version V+1)
      // This is the core of the race condition: we send V+1 before V.
      RegistrationInfo newerData = new RegistrationInfo(owner.getNodeId(), 6, false);
      Thread mutatorThread = new Thread(() -> addRegistration(owner, FEED, newerData), "mutator-thread");
      mutatorThread.start();

      // Give the mutator thread time to finish and send Version V+1 to the peer
      Thread.sleep(300);

      // Step 5: Resume the announcer thread so it finally sends the older state (Version V)
      resumeAnnounce.countDown();

      announcerThread.join(AWAIT_MS);
      mutatorThread.join(AWAIT_MS);

      // Step 6: Verify the fix.
      // If the bug still exists, the peer would reject Version V (as stale) and lose the initial data.
      // With the fix, the peer must have both the initial data and the new data.
      Set<RegistrationInfo> expectedData = new LinkedHashSet<>(initialData);
      expectedData.add(newerData);
      assertRegistrations("Peer must have all data, proving it did not reject Version V", peer, FEED, expectedData);
   }

   private static Object stateOf(JGroupsClusterManager manager) throws ReflectiveOperationException {
      var field = JGroupsClusterManager.class.getDeclaredField("state");
      field.setAccessible(true);
      return field.get(manager);
   }

   private static void invoke(Class<?> type, Object target, String name, Class<?>[] paramTypes, Object... args)
         throws ReflectiveOperationException {
      Method method = type.getDeclaredMethod(name, paramTypes);
      method.setAccessible(true);
      method.invoke(target, args);
   }
}

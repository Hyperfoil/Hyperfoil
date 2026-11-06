package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jgroups.util.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationListener;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;

abstract class ClusterManagerTestBase {
   protected static final String STACK = "jgroups-test.xml";
   protected static final long AWAIT_MS = 30_000;

   /** Overridden by the tests that need real sockets rather than SHARED_LOOPBACK. */
   protected String stack() {
      return STACK;
   }

   protected Vertx vertx;
   private final List<JGroupsClusterManager> managers = new ArrayList<>();

   @BeforeEach
   void setUpVertx() {
      vertx = Vertx.vertx();
   }

   @AfterEach
   void tearDownVertx() {
      for (JGroupsClusterManager manager : new ArrayList<>(managers)) {
         try {
            leave(manager);
         } catch (Exception | AssertionError e) {
            // the test may already have shut it down
         }
      }
      managers.clear();
      await(vertx.close());
   }

   /**
    * Each cluster gets its own name so that tests running in the same JVM cannot see each other through
    * SHARED_LOOPBACK's static registry.
    */
   protected JGroupsClusterManager join(String clusterName) {
      JGroupsClusterManager manager = new JGroupsClusterManager(stack(), clusterName);
      manager.init(vertx);
      managers.add(manager);
      Promise<Void> promise = Promise.promise();
      manager.join(promise);
      await(promise.future());
      return manager;
   }

   /**
    * Kills a node the way a {@code kill -9} or a deleted pod does: the channel stops sending and answering
    * without a graceful leave, so survivors have to notice through failure detection rather than being told.
    * The manager is dropped from the teardown list - calling {@code leave} on a shut-down channel only blocks.
    */
   protected void crash(JGroupsClusterManager manager) {
      try {
         Util.shutdown(manager.channel());
      } catch (Exception e) {
         throw new AssertionError("Could not shut down the channel", e);
      }
      managers.remove(manager);
   }

   protected void leave(JGroupsClusterManager manager) {
      Promise<Void> promise = Promise.promise();
      manager.leave(promise);
      await(promise.future());
      managers.remove(manager);
   }

   protected static <T> T await(io.vertx.core.Future<T> future) {
      try {
         return future.toCompletionStage().toCompletableFuture().get(AWAIT_MS, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
         throw new AssertionError("Future did not complete", e);
      }
   }

   protected static void setNodeInfo(JGroupsClusterManager manager, NodeInfo nodeInfo) {
      Promise<Void> promise = Promise.promise();
      manager.setNodeInfo(nodeInfo, promise);
      await(promise.future());
   }

   /**
    * @return the node info, or null if the manager does not know that node
    */
   protected static NodeInfo nodeInfoOf(JGroupsClusterManager manager, String nodeId) {
      Promise<NodeInfo> promise = Promise.promise();
      manager.getNodeInfo(nodeId, promise);
      return promise.future().succeeded() ? promise.future().result() : null;
   }

   protected static void addRegistration(JGroupsClusterManager manager, String address, RegistrationInfo registration) {
      Promise<Void> promise = Promise.promise();
      manager.addRegistration(address, registration, promise);
      await(promise.future());
   }

   protected static void removeRegistration(JGroupsClusterManager manager, String address,
         RegistrationInfo registration) {
      Promise<Void> promise = Promise.promise();
      manager.removeRegistration(address, registration, promise);
      await(promise.future());
   }

   protected static List<RegistrationInfo> registrations(JGroupsClusterManager manager, String address) {
      Promise<List<RegistrationInfo>> promise = Promise.promise();
      manager.getRegistrations(address, promise);
      return await(promise.future());
   }

   /** Polls rather than sleeping: the point of most assertions here is convergence, which has no completion signal. */
   protected static void awaitTrue(String what, BooleanSupplier condition) {
      awaitValue(what, () -> condition.getAsBoolean() ? Boolean.TRUE : null);
   }

   protected static <T> T awaitValue(String what, Supplier<T> condition) {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MS);
      T last = null;
      while (System.nanoTime() < deadline) {
         last = condition.get();
         if (last != null) {
            return last;
         }
         try {
            Thread.sleep(25);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(new TimeoutException("Interrupted while waiting for " + what));
         }
      }
      fail("Timed out after " + AWAIT_MS + "ms waiting for " + what);
      return null;
   }

   protected static void assertRegistrations(String what, JGroupsClusterManager manager, String address,
         Set<RegistrationInfo> expected) {
      awaitTrue(what, () -> Set.copyOf(registrations(manager, address)).equals(expected));
      // Re-assert outside the poll so a failure reports the actual value rather than just "timed out"
      assertEquals(expected, Set.copyOf(registrations(manager, address)), what);
   }

   protected static final class RecordingNodeListener implements NodeListener {
      final List<String> added = new CopyOnWriteArrayList<>();
      final List<String> left = new CopyOnWriteArrayList<>();

      @Override
      public void nodeAdded(String nodeID) {
         added.add(nodeID);
      }

      @Override
      public void nodeLeft(String nodeID) {
         left.add(nodeID);
      }
   }

   protected static final class RecordingRegistrationListener implements RegistrationListener {
      final List<RegistrationUpdateEvent> events = new CopyOnWriteArrayList<>();

      @Override
      public void registrationsUpdated(RegistrationUpdateEvent event) {
         events.add(event);
      }

      @Override
      public boolean wantsUpdatesFor(String address) {
         return true;
      }
   }
}

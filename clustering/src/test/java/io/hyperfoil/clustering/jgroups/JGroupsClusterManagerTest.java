package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * Three managers in one JVM over SHARED_LOOPBACK. Covers what Hyperfoil actually asks of a cluster manager:
 * membership, node info lookup and event bus subscription visibility.
 */
public class JGroupsClusterManagerTest extends ClusterManagerTestBase {
   private static final String CONTROL = "control-feed";
   private static final String STATS = "stats-feed";

   private static NodeInfo nodeInfo(int port) {
      return new NodeInfo("127.0.0.1", port, new JsonObject().put("port", port));
   }

   private static RegistrationInfo registration(JGroupsClusterManager manager, long seq) {
      return new RegistrationInfo(manager.getNodeId(), seq, false);
   }

   @Test
   public void shouldSeeEveryMember() {
      String cluster = "members";
      JGroupsClusterManager a = join(cluster);
      JGroupsClusterManager b = join(cluster);
      JGroupsClusterManager c = join(cluster);

      for (JGroupsClusterManager manager : List.of(a, b, c)) {
         awaitTrue("all three nodes visible from " + manager.getNodeId(), () -> manager.getNodes().size() == 3);
         assertEquals(Set.of(a.getNodeId(), b.getNodeId(), c.getNodeId()), Set.copyOf(manager.getNodes()));
         assertTrue(manager.isActive());
      }
      assertEquals(3, Set.of(a.getNodeId(), b.getNodeId(), c.getNodeId()).size(), "node ids must be distinct");
   }

   @Test
   public void shouldPropagateNodeInfo() {
      String cluster = "node-info";
      JGroupsClusterManager a = join(cluster);
      JGroupsClusterManager b = join(cluster);
      JGroupsClusterManager c = join(cluster);

      setNodeInfo(a, nodeInfo(1111));
      assertEquals(nodeInfo(1111), a.getNodeInfo());

      for (JGroupsClusterManager manager : List.of(b, c)) {
         assertEquals(nodeInfo(1111),
               awaitValue("A's node info at " + manager.getNodeId(), () -> nodeInfoOf(manager, a.getNodeId())));
      }

      // Overwriting must win everywhere, not be dropped as a same-owner conflict
      setNodeInfo(a, nodeInfo(2222));
      awaitTrue("A's updated node info at C", () -> nodeInfo(2222).equals(nodeInfoOf(c, a.getNodeId())));
   }

   @Test
   public void shouldFailLookupOfUnknownNode() {
      JGroupsClusterManager a = join("unknown-node");
      assertNull(nodeInfoOf(a, "no-such-node"));
   }

   @Test
   public void shouldPropagateRegistrations() {
      String cluster = "registrations";
      JGroupsClusterManager a = join(cluster);
      JGroupsClusterManager b = join(cluster);
      JGroupsClusterManager c = join(cluster);

      RegistrationInfo fromB = registration(b, 1);
      addRegistration(b, CONTROL, fromB);

      for (JGroupsClusterManager manager : List.of(a, b, c)) {
         assertRegistrations("B's registration at " + manager.getNodeId(), manager, CONTROL, Set.of(fromB));
      }

      RegistrationInfo fromC = registration(c, 1);
      addRegistration(c, CONTROL, fromC);
      assertRegistrations("both registrations at A", a, CONTROL, Set.of(fromB, fromC));

      removeRegistration(b, CONTROL, fromB);
      assertRegistrations("only C's registration left at A", a, CONTROL, Set.of(fromC));

      assertTrue(registrations(a, STATS).isEmpty(), "an address nobody registered must resolve to nothing");
   }

   @Test
   public void shouldGiveAJoinerTheExistingState() {
      String cluster = "late-joiner";
      JGroupsClusterManager a = join(cluster);
      RegistrationInfo fromA = registration(a, 1);
      setNodeInfo(a, nodeInfo(3333));
      addRegistration(a, CONTROL, fromA);

      // join() must not complete before the state pull has landed: the event bus starts sending immediately after
      JGroupsClusterManager b = join(cluster);
      assertEquals(Set.of(fromA), Set.copyOf(registrations(b, CONTROL)));
      assertEquals(nodeInfo(3333), nodeInfoOf(b, a.getNodeId()));
   }

   @Test
   public void shouldKeepLocalOnlyRegistrationsLocal() {
      String cluster = "local-only";
      JGroupsClusterManager a = join(cluster);
      JGroupsClusterManager b = join(cluster);

      RegistrationInfo local = new RegistrationInfo(a.getNodeId(), 1, true);
      RegistrationInfo shared = registration(a, 2);
      addRegistration(a, CONTROL, local);
      addRegistration(a, CONTROL, shared);

      assertEquals(Set.of(local, shared), Set.copyOf(registrations(a, CONTROL)));
      assertRegistrations("only the shared registration at B", b, CONTROL, Set.of(shared));

      removeRegistration(a, CONTROL, local);
      assertEquals(List.of(shared), registrations(a, CONTROL));
   }

   /**
    * The scenario the old {@code infinispan.xml} called out: <i>"We're often killing all agents at once; without
    * replicated state there are high chances that information (e.g. event bus subscriptions) would be lost."</i>
    * <p>
    * Both agents are killed without a graceful leave, so the survivor learns about them together - typically in a
    * single view that drops two members at once, which is the case a one-node-at-a-time purge would get wrong.
    * This is the logic behind "delete both agent pods simultaneously"; Kubernetes contributes nothing to it
    * beyond slower timing, so it is tested here rather than against a cluster.
    */
   @Test
   public void shouldDropEverythingWhenEveryAgentDiesAtOnce() {
      String cluster = "mass-departure";
      JGroupsClusterManager controller = join(cluster);
      JGroupsClusterManager agent1 = join(cluster);
      JGroupsClusterManager agent2 = join(cluster);

      RecordingNodeListener listener = new RecordingNodeListener();
      controller.nodeListener(listener);

      RegistrationInfo from1 = registration(agent1, 1);
      RegistrationInfo from2 = registration(agent2, 1);
      setNodeInfo(agent1, nodeInfo(5551));
      setNodeInfo(agent2, nodeInfo(5552));
      addRegistration(agent1, STATS, from1);
      addRegistration(agent2, STATS, from2);
      assertRegistrations("both agents registered", controller, STATS, Set.of(from1, from2));

      String id1 = agent1.getNodeId();
      String id2 = agent2.getNodeId();
      crash(agent1);
      crash(agent2);

      awaitTrue("controller alone in the view", () -> controller.getNodes().size() == 1);
      assertEquals(List.of(controller.getNodeId()), controller.getNodes());

      assertRegistrations("every agent registration purged", controller, STATS, Set.of());
      assertNull(nodeInfoOf(controller, id1), "agent1 node info must be purged");
      assertNull(nodeInfoOf(controller, id2), "agent2 node info must be purged");

      awaitTrue("nodeLeft for both agents", () -> listener.left.containsAll(List.of(id1, id2)));
      assertEquals(Set.of(id1, id2), Set.copyOf(listener.left));
      // A member dropped by one view and confirmed by the next must not be reported twice.
      assertEquals(2, listener.left.size(), "expected exactly one nodeLeft per agent, got " + listener.left);
      assertTrue(controller.isActive(), "the controller must survive losing every other member");
   }

   @Test
   public void shouldDropEverythingOwnedByADepartedNode() {
      String cluster = "departure";
      JGroupsClusterManager a = join(cluster);
      JGroupsClusterManager b = join(cluster);
      JGroupsClusterManager c = join(cluster);

      RecordingNodeListener listenerA = new RecordingNodeListener();
      RecordingNodeListener listenerC = new RecordingNodeListener();
      a.nodeListener(listenerA);
      c.nodeListener(listenerC);

      RegistrationInfo fromB = registration(b, 1);
      RegistrationInfo fromC = registration(c, 1);
      setNodeInfo(b, nodeInfo(4444));
      addRegistration(b, CONTROL, fromB);
      addRegistration(c, CONTROL, fromC);
      assertRegistrations("both registrations at A", a, CONTROL, Set.of(fromB, fromC));

      String departed = b.getNodeId();
      leave(b);

      for (JGroupsClusterManager manager : List.of(a, c)) {
         awaitTrue("B out of the view at " + manager.getNodeId(), () -> manager.getNodes().size() == 2);
         assertRegistrations("B's registration purged at " + manager.getNodeId(), manager, CONTROL, Set.of(fromC));
         assertNull(nodeInfoOf(manager, departed), "B's node info must be purged at " + manager.getNodeId());
      }

      // Every survivor purges independently, so every survivor must also be told
      for (RecordingNodeListener listener : List.of(listenerA, listenerC)) {
         awaitTrue("nodeLeft(B)", () -> listener.left.contains(departed));
         assertEquals(List.of(departed), listener.left);
      }
   }

   @Test
   public void shouldNotifyListeners() {
      String cluster = "listeners";
      JGroupsClusterManager a = join(cluster);
      RecordingNodeListener nodes = new RecordingNodeListener();
      RecordingRegistrationListener registrations = new RecordingRegistrationListener();
      a.nodeListener(nodes);
      a.registrationListener(registrations);

      JGroupsClusterManager b = join(cluster);
      awaitTrue("nodeAdded(B)", () -> nodes.added.contains(b.getNodeId()));

      RegistrationInfo fromB = registration(b, 1);
      addRegistration(b, CONTROL, fromB);
      awaitTrue("registrationsUpdated for " + CONTROL, () -> registrations.events.stream()
            .anyMatch(event -> CONTROL.equals(event.address()) && event.registrations().contains(fromB)));
      assertTrue(nodes.left.isEmpty());
   }

   @Test
   public void shouldFailUnsupportedSharedDataOperations() {
      JGroupsClusterManager a = join("shared-data");

      Promise<AsyncMap<String, String>> asyncMap = Promise.promise();
      a.getAsyncMap("map", asyncMap);
      assertUnsupported(asyncMap.future());

      Promise<Counter> counter = Promise.promise();
      a.getCounter("counter", counter);
      assertUnsupported(counter.future());

      Promise<Lock> lock = Promise.promise();
      a.getLockWithTimeout("lock", 1000, lock);
      assertUnsupported(lock.future());

      // getSyncMap degrades to node-local rather than failing, because Vert.x calls it eagerly during startup
      assertNotNull(a.getSyncMap("__vertx.haInfo"));
      assertSame(a.getSyncMap("whatever"), a.getSyncMap("whatever"));
   }

   private static void assertUnsupported(Future<?> future) {
      assertTrue(future.failed(), "expected the operation to fail rather than degrade silently");
      assertInstanceOf(UnsupportedOperationException.class, future.cause());
   }
}

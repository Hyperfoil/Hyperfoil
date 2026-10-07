package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * Round-trips every message shape through the codec. These catch ClusterSerializable offset bugs in milliseconds
 * instead of as a flaky clustered integration test.
 */
public class StateMessagesTest {

   @Test
   public void shouldRoundTripNodeInfoWithMetadata() {
      NodeInfo nodeInfo = new NodeInfo("10.0.0.7", 1234, new JsonObject().put("role", "agent"));
      StateMessages.Reader reader = new StateMessages.Reader(StateMessages.nodeInfo(42, nodeInfo));

      assertEquals(StateMessages.OP_NODE_INFO, reader.op());
      assertEquals(42, reader.readLong());
      assertEquals(nodeInfo, reader.readNodeInfo());
   }

   @Test
   public void shouldRoundTripNodeInfoWithoutMetadata() {
      NodeInfo nodeInfo = new NodeInfo("10.0.0.7", 1234, null);
      StateMessages.Reader reader = new StateMessages.Reader(StateMessages.nodeInfo(1, nodeInfo));

      reader.op();
      reader.readLong();
      NodeInfo read = reader.readNodeInfo();
      assertEquals("10.0.0.7", read.host());
      assertEquals(1234, read.port());
      assertNull(read.metadata());
   }

   @Test
   public void shouldRoundTripSubscription() {
      RegistrationInfo registration = new RegistrationInfo("node-a", 7, false);
      StateMessages.Reader reader = new StateMessages.Reader(
            StateMessages.subscription(StateMessages.OP_SUB_ADD, 3, "control-feed", registration));

      assertEquals(StateMessages.OP_SUB_ADD, reader.op());
      assertEquals(3, reader.readLong());
      assertEquals("control-feed", reader.readString());
      RegistrationInfo read = reader.readRegistration();
      assertEquals(registration, read);
      assertFalse(read.localOnly());
   }

   @Test
   public void shouldRoundTripLocalOnlySubscription() {
      RegistrationInfo registration = new RegistrationInfo("node-a", 7, true);
      StateMessages.Reader reader = new StateMessages.Reader(
            StateMessages.subscription(StateMessages.OP_SUB_REMOVE, 9, "stats-feed", registration));

      assertEquals(StateMessages.OP_SUB_REMOVE, reader.op());
      reader.readLong();
      reader.readString();
      assertTrue(reader.readRegistration().localOnly());
   }

   @Test
   public void shouldRoundTripNonAsciiAddress() {
      // appendString must use the UTF-8 byte length, not String.length()
      String address = "adresa-žluťoučký-þ";
      StateMessages.Reader reader = new StateMessages.Reader(StateMessages.subscription(
            StateMessages.OP_SUB_ADD, 1, address, new RegistrationInfo("n", 1, false)));

      reader.op();
      reader.readLong();
      assertEquals(address, reader.readString());
   }

   @Test
   public void shouldRoundTripFullSlice() {
      Map<String, Set<RegistrationInfo>> subs = new LinkedHashMap<>();
      subs.put("control-feed", new LinkedHashSet<>(Set.of(new RegistrationInfo("node-a", 1, false))));
      subs.put("stats-feed", new LinkedHashSet<>(
            List.of(new RegistrationInfo("node-a", 2, false), new RegistrationInfo("node-a", 3, false))));
      NodeInfo nodeInfo = new NodeInfo("127.0.0.1", 9999, new JsonObject().put("k", "v"));

      StateMessages.Reader reader = new StateMessages.Reader(StateMessages.slice(11, nodeInfo, subs));

      assertEquals(StateMessages.OP_SLICE, reader.op());
      assertEquals(11, reader.readLong());
      assertEquals(1, reader.readByte());
      assertEquals(nodeInfo, reader.readNodeInfo());
      assertEquals(2, reader.readInt());
      Map<String, Set<RegistrationInfo>> decoded = new LinkedHashMap<>();
      for (int i = 0; i < 2; ++i) {
         String address = reader.readString();
         int count = reader.readInt();
         Set<RegistrationInfo> registrations = new LinkedHashSet<>();
         for (int j = 0; j < count; ++j) {
            registrations.add(reader.readRegistration());
         }
         decoded.put(address, registrations);
      }
      assertEquals(subs, decoded);
   }

   @Test
   public void shouldRoundTripEmptySlice() {
      StateMessages.Reader reader = new StateMessages.Reader(StateMessages.slice(0, null, Map.of()));

      assertEquals(StateMessages.OP_SLICE, reader.op());
      assertEquals(0, reader.readLong());
      assertEquals(0, reader.readByte());
      assertEquals(0, reader.readInt());
   }

   @Test
   public void shouldEncodeStateRequestAsSingleByte() {
      byte[] request = StateMessages.stateRequest();
      assertEquals(1, request.length);
      assertEquals(StateMessages.OP_STATE_REQUEST, request[0]);
   }
}

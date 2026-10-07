package io.hyperfoil.clustering.jgroups;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.cluster.NodeInfo;
import io.vertx.core.spi.cluster.RegistrationInfo;

/**
 * Wire codec for the state replicated between {@link JGroupsClusterManager} instances.
 * <p>
 * {@link NodeInfo} and {@link RegistrationInfo} both implement
 * {@link io.vertx.core.shareddata.ClusterSerializable}, so their own {@code writeToBuffer}/{@code readFromBuffer}
 * is used and no external marshaller is required.
 */
final class StateMessages {
   static final byte OP_NODE_INFO = 1;
   static final byte OP_SUB_ADD = 2;
   static final byte OP_SUB_REMOVE = 3;
   static final byte OP_SLICE = 4;
   static final byte OP_STATE_REQUEST = 5;

   private StateMessages() {
   }

   static byte[] stateRequest() {
      return new byte[] { OP_STATE_REQUEST };
   }

   static byte[] nodeInfo(long version, NodeInfo nodeInfo) {
      Buffer buffer = Buffer.buffer();
      buffer.appendByte(OP_NODE_INFO).appendLong(version);
      nodeInfo.writeToBuffer(buffer);
      return buffer.getBytes();
   }

   static byte[] subscription(byte op, long version, String address, RegistrationInfo registration) {
      Buffer buffer = Buffer.buffer();
      buffer.appendByte(op).appendLong(version);
      appendString(buffer, address);
      registration.writeToBuffer(buffer);
      return buffer.getBytes();
   }

   static byte[] slice(long version, NodeInfo nodeInfo, Map<String, Set<RegistrationInfo>> subs) {
      Buffer buffer = Buffer.buffer();
      buffer.appendByte(OP_SLICE).appendLong(version);
      if (nodeInfo == null) {
         buffer.appendByte((byte) 0);
      } else {
         buffer.appendByte((byte) 1);
         nodeInfo.writeToBuffer(buffer);
      }
      buffer.appendInt(subs.size());
      for (Map.Entry<String, Set<RegistrationInfo>> entry : subs.entrySet()) {
         appendString(buffer, entry.getKey());
         buffer.appendInt(entry.getValue().size());
         for (RegistrationInfo registration : entry.getValue()) {
            registration.writeToBuffer(buffer);
         }
      }
      return buffer.getBytes();
   }

   private static void appendString(Buffer buffer, String value) {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      buffer.appendInt(bytes.length).appendBytes(bytes);
   }

   /**
    * Sequential reader over an encoded message. Not thread-safe; a reader is used by a single decoding call.
    */
   static final class Reader {
      private final Buffer buffer;
      private int pos;

      Reader(byte[] bytes) {
         this.buffer = Buffer.buffer(bytes);
      }

      byte op() {
         return readByte();
      }

      byte readByte() {
         byte value = buffer.getByte(pos);
         pos += 1;
         return value;
      }

      int readInt() {
         int value = buffer.getInt(pos);
         pos += 4;
         return value;
      }

      long readLong() {
         long value = buffer.getLong(pos);
         pos += 8;
         return value;
      }

      String readString() {
         int length = readInt();
         String value = buffer.getString(pos, pos + length, StandardCharsets.UTF_8.name());
         pos += length;
         return value;
      }

      NodeInfo readNodeInfo() {
         NodeInfo nodeInfo = new NodeInfo();
         pos = nodeInfo.readFromBuffer(pos, buffer);
         return nodeInfo;
      }

      RegistrationInfo readRegistration() {
         RegistrationInfo registration = new RegistrationInfo();
         pos = registration.readFromBuffer(pos, buffer);
         return registration;
      }
   }
}

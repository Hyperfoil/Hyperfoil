package io.sailrocket.core.extractors;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.Session;
import io.sailrocket.core.machine.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ArrayRecorder implements Session.Processor, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(ArrayRecorder.class);
   private final String var;
   private final int maxSize;

   public ArrayRecorder(String var, int maxSize) {
      this.var = var;
      this.maxSize = maxSize;
   }

   public void before(Session session) {
      Object array = fetch(session);
      int length = Array.getLength(array);
      for (int i = 0; i < length; ++i) {
         Array.set(array, i, null);
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      Object array = fetch(session);
      int arrayLength = Array.getLength(array);
      String value = data.toString(offset, length, StandardCharsets.UTF_8);
      for (int i = 0; i < arrayLength; ++i) {
         if (Array.get(array, i) != null) continue;
         Array.set(array, i, value);
         return;
      }
      log.warn("Exceed maximum size of the array {} ({}), dropping value {}", var, maxSize, value);
   }

   private Object fetch(Session session) {
      Object array = session.getObject(this.var);
      if (array == null) {
         throw new IllegalStateException("Variable " + var + " has not been declared.");
      } else if (!array.getClass().isArray()) {
         throw new IllegalStateException("Variable " + var + " does not contain an array: " + array);
      }
      return array;
   }

   @Override
   public void reserve(io.sailrocket.core.machine.Session session) {
      session.declare(var);
      session.setObject(var, new String[maxSize]);
   }
}

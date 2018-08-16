package io.sailrocket.core.extractors;

import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.Session;
import io.sailrocket.core.machine.ResourceUtilizer;

public class ArrayRecorder<T> implements Session.Processor, ResourceUtilizer {
   private final String var;
   private final Supplier<T[]> arraySupplier;

   public ArrayRecorder(String var, Supplier<T[]> arraySupplier) {
      this.var = var;
      this.arraySupplier = arraySupplier;
   }

   public void before(Session session) {
      Object array = fetch(session);
      int length = Array.getLength(array);
      for (int i = 0; i < length; ++i) {
         Array.set(array, i, null);
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length) {
      Object array = fetch(session);
      int arrayLength = Array.getLength(array);
      for (int i = 0; i < arrayLength; ++i) {
         if (Array.get(array, i) != null) continue;
         Array.set(array, i, data.toString(offset, length, Charset.forName("UTF-8")));
         break;
      }
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
      session.setObject(var, arraySupplier.get());
   }
}

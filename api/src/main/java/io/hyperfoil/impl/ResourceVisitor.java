package io.hyperfoil.impl;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Visitor;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.WriteAccess;

public class ResourceVisitor implements Visitor {
   private final ArrayList<ResourceUtilizer> resourceUtilizers = new ArrayList<>();
   private final ArrayList<ReadAccess> reads = new ArrayList<>();
   private final ArrayList<WriteAccess> writes = new ArrayList<>();
   private final Set<Object> seen = new HashSet<>();

   public ResourceVisitor() {
   }

   public ResourceVisitor(Sequence sequence) {
      for (var step : sequence.steps()) {
         visit(null, step, null);
      }
   }

   public ResourceUtilizer[] resourceUtilizers() {
      return resourceUtilizers.toArray(new ResourceUtilizer[0]);
   }

   public ReadAccess[] reads() {
      return reads.toArray(new ReadAccess[0]);
   }

   public WriteAccess[] writes() {
      return writes.toArray(new WriteAccess[0]);
   }

   @Override
   public boolean visit(String name, Object value, Type fieldType) {
      if (value == null) {
         return false;
      } else if (!seen.add(value)) {
         return false;
      } else if (value instanceof ReadAccess) {
         reads.add((ReadAccess) value);
         if (value instanceof WriteAccess) {
            writes.add((WriteAccess) value);
         }
      } else if (value instanceof ResourceUtilizer) {
         resourceUtilizers.add((ResourceUtilizer) value);
         ReflectionAcceptor.accept(value, this);
      } else if (value instanceof Collection) {
         ((Collection<?>) value).forEach(item -> visit(null, item, null));
      } else if (value instanceof Map) {
         ((Map<?, ?>) value).forEach((k, v) -> visit(null, v, null));
      } else if (ReflectionAcceptor.isScalar(value)) {
         return false;
      } else if (value.getClass().isArray()) {
         int length = Array.getLength(value);
         for (int i = 0; i < length; ++i) {
            visit(null, Array.get(value, i), null);
         }
      } else {
         ReflectionAcceptor.accept(value, this);
      }
      // the return value doesn't matter here
      return false;
   }
}

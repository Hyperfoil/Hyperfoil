package io.hyperfoil.core.generators;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableToIntFunction;

public class IntValueProviderBuilder<P> implements InitFromParam<IntValueProviderBuilder<P>> {
   private final P parent;
   private final Integer defaultValue;
   private Integer value;
   private String fromVar;

   public IntValueProviderBuilder(P parent, Integer defaultValue) {
      this.parent = parent;
      this.defaultValue = defaultValue;
   }

   public P end() {
      return parent;
   }

   /**
    * Initialize with a constant value.
    *
    * @param param Constant value.
    * @return Self.
    */
   @Override
   public IntValueProviderBuilder<P> init(String param) {
      try {
         value = Integer.parseInt(param.trim());
      } catch (NumberFormatException e) {
         throw new BenchmarkDefinitionException("Cannot convert " + param + " to integer value.");
      }
      return this;
   }

   /**
    * Initialize with a constant value.
    *
    * @param value Constant value.
    * @return Self.
    */
   public IntValueProviderBuilder<P> value(int value) {
      this.value = value;
      return this;
   }

   /**
    * Initialize with a value from session variable.
    *
    * @param fromVar Variable name.
    * @return Self.
    */
   public IntValueProviderBuilder<P> fromVar(String fromVar) {
      this.fromVar = fromVar;
      return this;
   }

   public SerializableToIntFunction<Session> build() {
      if (value == null && fromVar == null && defaultValue == null) {
         throw new BenchmarkDefinitionException("Must set either 'value' or 'fromVar'.");
      } else if (value != null && fromVar != null) {
         throw new BenchmarkDefinitionException("Must set one of: 'value' or 'fromVar'.");
      } else if (fromVar != null) {
         ReadAccess access = SessionFactory.readAccess(fromVar);
         return access::getInt;
      } else {
         int unbox = value == null ? defaultValue : value;
         return new ConstValue(unbox);
      }
   }

   private int assertionValue() {
      if (value == null && defaultValue == null) {
         return 0;
      } else if (value != null) {
         return value;
      } else {
         return defaultValue;
      }
   }

   public int compareTo(IntValueProviderBuilder<?> other) {
      if (fromVar != null || other.fromVar != null) {
         return 0;
      }
      int v1 = assertionValue();
      int v2 = other.assertionValue();
      return Integer.compare(v1, v2);
   }

   private static class ConstValue implements SerializableToIntFunction<Session> {
      private final int value;

      private ConstValue(int value) {
         this.value = value;
      }

      @Override
      public int applyAsInt(Session session) {
         return value;
      }
   }
}

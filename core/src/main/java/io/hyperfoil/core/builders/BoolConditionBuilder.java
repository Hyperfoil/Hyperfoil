package io.hyperfoil.core.builders;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.InitFromParam;

public class BoolConditionBuilder<B extends BoolConditionBuilder<B, P>, P> implements BuilderBase<B>, InitFromParam<B> {
   protected final Object parent;
   protected boolean value = true;

   public BoolConditionBuilder(P parent) {
      this.parent = parent;
   }

   @SuppressWarnings("unchecked")
   protected B self() {
      return (B) this;
   }

   /**
    * @param param Empty or <code>true</code> for true, <code>false</code> for false.
    * @return Self.
    */
   @Override
   public B init(String param) {
      if (param.isEmpty()) {
         // default to true
         return value(true);
      } else if (param.equalsIgnoreCase("true")) {
         return value(true);
      } else if (param.equalsIgnoreCase("false")) {
         return value(false);
      } else {
         throw new BenchmarkDefinitionException("Cannot parse '" + param + "' into boolean value.");
      }
   }

   /**
    * Expected value.
    *
    * @param value True or false.
    * @return Self.
    */
   public B value(boolean value) {
      this.value = value;
      return self();
   }

   public Object end() {
      return parent;
   }
}

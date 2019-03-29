package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.function.SerializableIntPredicate;
import io.hyperfoil.function.SerializableSupplier;

public class AwaitIntStep implements Step {
   private final String var;
   private final SerializableIntPredicate predicate;

   public AwaitIntStep(String var, SerializableIntPredicate predicate) {
      this.var = var;
      this.predicate = predicate;
   }

   @Override
   public boolean invoke(Session session) {
      if (session.isSet(var)) {
         return predicate == null || predicate.test(session.getInt(var));
      }
      return false;
   }

   public static class Builder extends IntegerConditionBuilder<Builder> {
      private String var;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new AwaitIntStep(var, buildPredicate()));
      }
   }
}

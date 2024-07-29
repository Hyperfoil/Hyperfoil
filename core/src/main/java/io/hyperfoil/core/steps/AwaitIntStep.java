package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.IntCondition;
import io.hyperfoil.core.builders.IntConditionBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class AwaitIntStep implements Step {
   private final ReadAccess var;
   private final IntCondition.Predicate predicate;

   public AwaitIntStep(ReadAccess var, IntCondition.Predicate predicate) {
      this.var = var;
      this.predicate = predicate;
   }

   @Override
   public boolean invoke(Session session) {
      if (var.isSet(session)) {
         return predicate == null || predicate.test(session, var.getInt(session));
      }
      return false;
   }

   /**
    * Block current sequence until condition becomes true.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("awaitInt")
   public static class Builder extends IntConditionBuilder<Builder, BaseSequenceBuilder<?>> implements StepBuilder<Builder> {
      private String var;

      public Builder() {
         super(null);
      }

      public Builder(BaseSequenceBuilder<?> parent) {
         super(parent);
      }

      /**
       * Variable name storing the compared value.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder var(String var) {
         this.var = var;
         return this;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new AwaitIntStep(SessionFactory.readAccess(var), buildPredicate()));
      }

      public BaseSequenceBuilder<?> endStep() {
         return parent;
      }
   }
}

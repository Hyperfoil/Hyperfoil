package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.IntCondition;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableIntPredicate;

public class AwaitIntStep implements Step {
   private final Access var;
   private final SerializableIntPredicate predicate;

   public AwaitIntStep(Access var, SerializableIntPredicate predicate) {
      this.var = var;
      this.predicate = predicate;
   }

   @Override
   public boolean invoke(Session session) {
      if (var.isSet(session)) {
         return predicate == null || predicate.test(var.getInt(session));
      }
      return false;
   }

   /**
    * Block current sequence until condition becomes true.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("awaitInt")
   public static class Builder extends IntCondition.BaseBuilder<Builder, BaseSequenceBuilder> implements StepBuilder<Builder> {
      private String var;

      public Builder() {
         super(null);
      }

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
         if (parent != null) {
            parent.stepBuilder(this);
         }
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
         return Collections.singletonList(new AwaitIntStep(SessionFactory.access(var), buildPredicate()));
      }

      public BaseSequenceBuilder endStep() {
         return parent;
      }
   }
}

package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.IntCondition;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableIntPredicate;
import io.hyperfoil.function.SerializableSupplier;

public class AwaitIntStep implements Step {
   private final Access var;
   private final SerializableIntPredicate predicate;

   public AwaitIntStep(String var, SerializableIntPredicate predicate) {
      this.var = SessionFactory.access(var);
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
   public static class Builder extends IntCondition.BaseBuilder<Builder> implements StepBuilder {
      private final BaseSequenceBuilder parent;
      private String var;

      public Builder(BaseSequenceBuilder parent) {
         this.parent = parent;
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
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new AwaitIntStep(var, buildPredicate()));
      }

      @Override
      public BaseSequenceBuilder endStep() {
         return parent;
      }
   }
}

package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.DependencyStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

public class ForeachStep extends DependencyStep implements ResourceUtilizer {
   private final Access fromVar;
   private final Access counterVar;
   private final String sequence;

   public ForeachStep(SerializableSupplier<Sequence> sequence, Access[] dependencies, String fromVar, String counterVar, String template) {
      super(sequence, dependencies);
      this.fromVar = SessionFactory.access(fromVar);
      this.counterVar = SessionFactory.access(counterVar);
      this.sequence = template;
   }

   @Override
   public boolean invoke(Session session) {
      if (!super.invoke(session)) {
         return false;
      }
      Object value = fromVar.getObject(session);
      if (!(value instanceof Session.Var[])) {
         throw new IllegalStateException("Variable " + fromVar + " does not contain var array: " + value);
      }
      // Java array polymorphism is useful at times...
      Session.Var[] array = (Session.Var[]) value;
      int i = 0;
      for (; i < array.length; i++) {
         if (!array[i].isSet()) break;
         session.phase().scenario().sequence(sequence).instantiate(session, i);
      }
      if (counterVar != null) {
         counterVar.setInt(session, i);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      if (counterVar != null) {
         counterVar.declareInt(session);
      }
   }

   /**
    * Instantiate new sequences based on array variable content.
    */
   public static class Builder extends DependencyStepBuilder<Builder> {
      private String fromVar;
      private String counterVar;
      private String sequence;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      /**
       * Variable holding the array.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         dependency(fromVar);
         return this;
      }

      /**
       * Variable to be set to the number of created sequences (optional).
       */
      public Builder counterVar(String counterVar) {
         this.counterVar = counterVar;
         return this;
      }

      /**
       * Name of the instantiated sequence.
       */
      public Builder sequence(String sequence) {
         this.sequence = sequence;
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         if (this.sequence == null) {
            throw new BenchmarkDefinitionException("Template sequence must be defined");
         }
         return Collections.singletonList(new ForeachStep(sequence, dependencies(), fromVar, counterVar, this.sequence));
      }
   }
}

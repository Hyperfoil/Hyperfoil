package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.builders.DependencyStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class ForeachStep extends DependencyStep implements ResourceUtilizer {
   private final Access fromVar;
   private final Access counterVar;
   private final String sequence;

   public ForeachStep(Access[] dependencies, Access fromVar, Access counterVar, String sequence) {
      super(dependencies);
      this.fromVar = fromVar;
      this.counterVar = counterVar;
      this.sequence = sequence;
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
         SequenceInstance instance = session.startSequence(sequence, false, Session.ConcurrencyPolicy.FAIL);
         // This is a bit fragile; we rely on the fact that
         // 1) instance always gets the lowest possible index
         // 2) this loop starts sequences all at once, so the sequence cannot finish before we start them all
         if (instance.index() != i) {
            throw new IllegalStateException("This step assumes that there are no already running instances of " + sequence);
         }
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
   @MetaInfServices(StepBuilder.class)
   @Name("foreach")
   public static class Builder extends DependencyStepBuilder<Builder> {
      private String fromVar;
      private String counterVar;
      private String sequence;

      /**
       * Variable holding the array.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         dependency(fromVar);
         return this;
      }

      /**
       * Variable to be set to the number of created sequences (optional).
       *
       * @param counterVar Variable name.
       * @return Self.
       */
      public Builder counterVar(String counterVar) {
         this.counterVar = counterVar;
         return this;
      }

      /**
       * Name of the instantiated sequence.
       *
       * @param sequence Sequence name.
       * @return Self.
       */
      public Builder sequence(String sequence) {
         this.sequence = sequence;
         return this;
      }

      @Override
      public List<Step> build() {
         if (this.sequence == null) {
            throw new BenchmarkDefinitionException("Template sequence must be defined");
         }
         return Collections.singletonList(new ForeachStep(dependencies(),
               SessionFactory.access(fromVar), SessionFactory.access(counterVar), this.sequence));
      }
   }
}

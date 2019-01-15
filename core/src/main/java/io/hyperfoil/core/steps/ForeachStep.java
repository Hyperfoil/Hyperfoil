package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.VarReference;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseSequenceBuilder;
import io.hyperfoil.core.builders.DependencyStepBuilder;
import io.hyperfoil.core.session.SimpleVarReference;

public class ForeachStep extends DependencyStep implements ResourceUtilizer {
   private final String dataVar;
   private final String counterVar;
   private final Sequence template;

   public ForeachStep(VarReference[] dependencies, String dataVar, String counterVar, Sequence template) {
      super(dependencies);
      this.dataVar = dataVar;
      this.counterVar = counterVar;
      this.template = template;
   }

   @Override
   public boolean invoke(Session session) {
      if (!super.invoke(session)) {
         return false;
      }
      Object value = session.getObject(dataVar);
      if (!(value instanceof Session.Var[])) {
         throw new IllegalStateException("Variable " + dataVar + " does not contain var array: " + value);
      }
      // Java array polymorphism is useful at times...
      Session.Var[] array = (Session.Var[]) value;
      int i = 0;
      for (; i < array.length; i++) {
         if (!array[i].isSet()) break;
         template.instantiate(session, i);
      }
      if (counterVar != null) {
         session.setInt(counterVar, i);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declareInt(counterVar);
   }

   public static class Builder extends DependencyStepBuilder {
      private String dataVar;
      private String counterVar;
      private String sequenceTemplate;

      public Builder(BaseSequenceBuilder parent, String dataVar, String counterVar) {
         super(parent);
         this.dataVar = dataVar;
         this.counterVar = counterVar;
         dependency(new SimpleVarReference(dataVar));
      }

      public Builder dataVar(String dataVar) {
         this.dataVar = dataVar;
         return this;
      }

      public Builder counterVar(String counterVar) {
         this.counterVar = counterVar;
         return this;
      }

      public Builder sequence(String sequenceTemplate) {
         this.sequenceTemplate = sequenceTemplate;
         return this;
      }

      @Override
      public List<Step> build() {
         if (sequenceTemplate == null) {
            throw new BenchmarkDefinitionException("Template sequence must be defined");
         }
         Sequence sequence = parent.end().endSequence().findSequence(sequenceTemplate).build();
         return Collections.singletonList(new ForeachStep(dependencies(), dataVar, counterVar, sequence));
      }

      public Builder sequenceTemplate(String sequenceTemplate) {
         this.sequenceTemplate = sequenceTemplate;
         return this;
      }
   }
}

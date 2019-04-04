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
   private final Access dataVar;
   private final Access counterVar;
   private final String sequence;

   public ForeachStep(SerializableSupplier<Sequence> sequence, Access[] dependencies, String dataVar, String counterVar, String template) {
      super(sequence, dependencies);
      this.dataVar = SessionFactory.access(dataVar);
      this.counterVar = SessionFactory.access(counterVar);
      this.sequence = template;
   }

   @Override
   public boolean invoke(Session session) {
      if (!super.invoke(session)) {
         return false;
      }
      Object value = dataVar.getObject(session);
      if (!(value instanceof Session.Var[])) {
         throw new IllegalStateException("Variable " + dataVar + " does not contain var array: " + value);
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
      counterVar.declareInt(session);
   }

   public static class Builder extends DependencyStepBuilder<Builder> {
      private String dataVar;
      private String counterVar;
      private String sequence;

      public Builder(BaseSequenceBuilder parent, String dataVar, String counterVar) {
         super(parent);
         this.dataVar = dataVar;
         this.counterVar = counterVar;
         dependency(dataVar);
      }

      public Builder dataVar(String dataVar) {
         this.dataVar = dataVar;
         return this;
      }

      public Builder counterVar(String counterVar) {
         this.counterVar = counterVar;
         return this;
      }

      public Builder sequence(String sequence) {
         this.sequence = sequence;
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         if (this.sequence == null) {
            throw new BenchmarkDefinitionException("Template sequence must be defined");
         }
         return Collections.singletonList(new ForeachStep(sequence, dependencies(), dataVar, counterVar, this.sequence));
      }
   }
}

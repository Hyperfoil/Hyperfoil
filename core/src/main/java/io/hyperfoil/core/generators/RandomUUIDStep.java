package io.hyperfoil.core.generators;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.LongFastUUID;

public class RandomUUIDStep implements Step, ResourceUtilizer {
   private final Access toVar;

   public RandomUUIDStep(Access toVar) {
      this.toVar = toVar;
   }

   @Override
   public boolean invoke(Session session) {
      toVar.setObject(session, LongFastUUID.randomUUID());
      return true;
   }

   @Override
   public void reserve(Session session) {
      toVar.declareObject(session);
   }

   /**
    * Stores random string into session variable based on the generator.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("randomUUID")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      private String toVar;

      @Override
      public Builder init(String rangeToVar) {
         if (rangeToVar == null) {
            return this;
         }
         toVar = rangeToVar.trim();
         return this;
      }

      /**
       * Variable name to store the result.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder toVar(String var) {
         this.toVar = var;
         return this;
      }

      @Override
      public List<Step> build() {
         if (toVar == null || toVar.isEmpty()) {
            throw new BenchmarkDefinitionException("Missing target var.");
         }
         return Collections.singletonList(new RandomUUIDStep(SessionFactory.access(toVar)));
      }
   }
}

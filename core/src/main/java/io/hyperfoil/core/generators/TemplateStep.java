package io.hyperfoil.core.generators;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

public class TemplateStep implements Step, ResourceUtilizer {
   private final Pattern pattern;
   private final Access toVar;

   public TemplateStep(Pattern pattern, String toVar) {
      this.pattern = pattern;
      this.toVar = SessionFactory.access(toVar);
   }

   @Override
   public boolean invoke(Session session) {
      toVar.setObject(session, pattern.apply(session));
      return true;
   }

   @Override
   public void reserve(Session session) {
      toVar.declareObject(session);
   }

   /**
    * Format pattern into session variable.
    */
   public static class Builder extends BaseStepBuilder {
      private Pattern pattern;
      private String toVar;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      /**
       * Pattern to be encoded, e.g. <code>foo${variable}bar${another-variable}</code>
       */
      public Builder pattern(String pattern) {
         this.pattern = new Pattern(pattern, false);
         return this;
      }

      /**
       * Variable name to store the result.
       */
      public Builder toVar(String var) {
         this.toVar = var;
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         if (pattern == null) {
            throw new BenchmarkDefinitionException("Missing pattern for template.");
         }
         if (toVar == null) {
            throw new BenchmarkDefinitionException("Missing target var for template.");
         }
         return Collections.singletonList(new TemplateStep(pattern, toVar));
      }
   }
}

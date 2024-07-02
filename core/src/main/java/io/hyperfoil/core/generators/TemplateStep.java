package io.hyperfoil.core.generators;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class TemplateStep implements Step {
   private final Pattern pattern;
   private final ObjectAccess toVar;

   public TemplateStep(Pattern pattern, ObjectAccess toVar) {
      this.pattern = pattern;
      this.toVar = toVar;
   }

   @Override
   public boolean invoke(Session session) {
      toVar.setObject(session, pattern.apply(session));
      return true;
   }

   /**
    * Format <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> into session variable.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("template")
   public static class Builder extends BaseStepBuilder<Builder> {
      private String pattern;
      private String toVar;

      /**
       * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">Pattern</a>
       * to be encoded, e.g. <code>foo${variable}bar${another-variable}</code>
       *
       * @param pattern Template pattern.
       * @return Self.
       */
      public Builder pattern(String pattern) {
         this.pattern = pattern;
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
         if (pattern == null) {
            throw new BenchmarkDefinitionException("Missing pattern for template.");
         }
         if (toVar == null) {
            throw new BenchmarkDefinitionException("Missing target var for template.");
         }
         return Collections.singletonList(new TemplateStep(new Pattern(pattern, false), SessionFactory.objectAccess(toVar)));
      }
   }
}

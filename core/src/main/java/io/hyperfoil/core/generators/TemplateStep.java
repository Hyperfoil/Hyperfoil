package io.hyperfoil.core.generators;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;

public class TemplateStep implements Step, ResourceUtilizer {
   private final Pattern pattern;
   private final String var;

   public TemplateStep(Pattern pattern, String var) {
      this.pattern = pattern;
      this.var = var;
   }

   @Override
   public boolean invoke(Session session) {
      session.setObject(var, pattern.apply(session));
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declare(var);
   }

   public static class Builder extends BaseStepBuilder {
      private Pattern pattern;
      private String var;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder pattern(String pattern) {
         this.pattern = new Pattern(pattern);
         return this;
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      @Override
      public List<Step> build() {
         if (pattern == null) {
            throw new BenchmarkDefinitionException("Missing pattern for template.");
         }
         if (var == null) {
            throw new BenchmarkDefinitionException("Missing target var for template.");
         }
         return Collections.singletonList(new TemplateStep(pattern, var));
      }
   }
}

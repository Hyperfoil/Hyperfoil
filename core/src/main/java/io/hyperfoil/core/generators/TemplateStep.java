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
   private final Access var;

   public TemplateStep(Pattern pattern, String var) {
      this.pattern = pattern;
      this.var = SessionFactory.access(var);
   }

   @Override
   public boolean invoke(Session session) {
      var.setObject(session, pattern.apply(session));
      return true;
   }

   @Override
   public void reserve(Session session) {
      var.declareObject(session);
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
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
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

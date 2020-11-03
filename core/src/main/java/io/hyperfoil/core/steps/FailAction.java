package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.Condition;

public class FailAction implements Action {
   private final String message;
   private final Condition condition;

   public FailAction(String message, Condition condition) {
      this.message = message;
      this.condition = condition;
   }

   @Override
   public void run(Session session) {
      if (condition == null || condition.test(session)) {
         session.fail(new BenchmarkExecutionException("Terminating benchmark" + (message == null ? "." : ": " + message)));
      }
   }

   @MetaInfServices(Action.Builder.class)
   @Name("fail")
   public static class Builder implements Action.Builder {
      private String message;

      @Embed
      public final Condition.TypesBuilder<Builder> condition = new Condition.TypesBuilder<>(this);

      public Builder message(String message) {
         this.message = message;
         return this;
      }

      @Override
      public Action build() {
         return new FailAction(message, condition.buildCondition());
      }
   }
}

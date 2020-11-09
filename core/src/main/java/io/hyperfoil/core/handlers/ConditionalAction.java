package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.Condition;

public class ConditionalAction extends BaseDelegatingAction {
   private final Condition condition;

   public ConditionalAction(Condition condition, Action[] actions) {
      super(actions);
      this.condition = condition;
   }

   @Override
   public void run(Session session) {
      if (condition.test(session)) {
         super.run(session);
      }
   }

   /**
    * Perform an action or sequence of actions conditionally.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("conditional")
   public static class Builder extends BaseDelegatingAction.Builder<Builder> {
      private Condition.TypesBuilder<Builder> condition = new Condition.TypesBuilder<>(this);

      @Embed
      public Condition.TypesBuilder<Builder> condition() {
         return condition;
      }

      @Override
      public Action build() {
         Condition condition = this.condition.buildCondition();
         if (condition == null) {
            throw new BenchmarkDefinitionException("Conditional action requires a condition.");
         }
         return new ConditionalAction(condition, buildActions());
      }
   }
}

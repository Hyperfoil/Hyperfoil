package io.hyperfoil.core.steps;

import java.util.function.Predicate;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;

public class AwaitConditionStep implements Step {
   private final Predicate<Session> condition;

   public AwaitConditionStep(Predicate<Session> condition) {
      this.condition = condition;
   }

   @Override
   public boolean invoke(Session session) {
      return condition.test(session);
   }
}

package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;

public class AwaitAllResponsesStep implements Step {
   @Override
   public boolean invoke(Session session) {
      return session.requestPool().isFull();
   }
}

package io.sailrocket.core.steps;

import io.sailrocket.api.session.Session;
import io.sailrocket.api.config.Step;

public class AwaitAllResponsesStep implements Step {
   @Override
   public boolean prepare(Session session) {
      return session.requestQueue().isFull();
   }

   @Override
   public void invoke(Session session) {
      // noop
   }
}

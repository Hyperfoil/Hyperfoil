package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;

public class AwaitAllResponsesStep implements Step {
   @Override
   public boolean invoke(Session session) {
      return session.httpRequestPool().isFull();
   }

   /**
    * Block current sequence until all requests receive the response.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("awaitAllResponses")
   public static class Builder implements StepBuilder<Builder> {
      @Override
      public List<Step> build() {
         return Collections.singletonList(new AwaitAllResponsesStep());
      }
   }
}

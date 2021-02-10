package io.hyperfoil.hotrod.steps;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.hotrod.resource.HotRodResource;

public class HotRodResponseStep implements Step {

   final HotRodResource.Key futureWrapperKey;

   protected HotRodResponseStep(HotRodResource.Key futureWrapperKey) {
      this.futureWrapperKey = futureWrapperKey;
   }

   @Override
   public boolean invoke(Session session) {
      HotRodResource resource = session.getResource(futureWrapperKey);
      boolean complete = resource.isComplete();
      return complete;
   }
}
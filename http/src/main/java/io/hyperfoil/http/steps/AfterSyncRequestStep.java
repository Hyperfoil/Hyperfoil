package io.hyperfoil.http.steps;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.util.BitSetResource;

class AfterSyncRequestStep implements Step {
   private final Session.ResourceKey<BitSetResource> key;

   AfterSyncRequestStep(Session.ResourceKey<BitSetResource> key) {
      this.key = key;
   }

   @Override
   public boolean invoke(Session session) {
      BitSetResource resource = session.getResource(key);
      return resource.get(session.currentSequence().index());
   }
}

package io.hyperfoil.http.steps;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.util.BitSetResource;

class BeforeSyncRequestStep implements Step, ResourceUtilizer, Session.ResourceKey<BitSetResource> {
   @Override
   public boolean invoke(Session s) {
      BitSetResource resource = s.getResource(this);
      resource.clear(s.currentSequence().index());
      return true;
   }

   @Override
   public void reserve(Session session) {
      int concurrency = session.currentSequence().definition().concurrency();
      session.declareResources().add(this, () -> BitSetResource.with(concurrency), true);
   }
}

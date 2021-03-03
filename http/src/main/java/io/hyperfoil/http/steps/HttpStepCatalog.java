package io.hyperfoil.http.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.impl.StepCatalogFactory;

public class HttpStepCatalog extends StepCatalog {
   public static final Class<HttpStepCatalog> SC = HttpStepCatalog.class;

   HttpStepCatalog(BaseSequenceBuilder parent) {
      super(parent);
   }

   /**
    * Issue a HTTP request.
    *
    * @param method HTTP method.
    * @return Builder.
    */
   public HttpRequestStepBuilder httpRequest(HttpMethod method) {
      return new HttpRequestStepBuilder().addTo(parent).method(method);
   }

   /**
    * Block current sequence until all requests receive the response.
    *
    * @return This sequence.
    */
   public BaseSequenceBuilder awaitAllResponses() {
      return parent.step(new AwaitAllResponsesStep());
   }


   /**
    * Drop all entries from HTTP cache in the session.
    *
    * @return This sequence.
    */
   public BaseSequenceBuilder clearHttpCache() {
      return parent.step(new StepBuilder.ActionStep(new ClearHttpCacheAction()));
   }

   @MetaInfServices(StepCatalogFactory.class)
   public static class Factory implements StepCatalogFactory {
      @Override
      public Class<? extends Step.Catalog> clazz() {
         return HttpStepCatalog.class;
      }

      @Override
      public Step.Catalog create(BaseSequenceBuilder sequenceBuilder) {
         return new HttpStepCatalog(sequenceBuilder);
      }
   }
}

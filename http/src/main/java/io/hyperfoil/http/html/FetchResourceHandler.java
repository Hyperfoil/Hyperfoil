package io.hyperfoil.http.html;

import static io.hyperfoil.core.session.SessionFactory.sequenceScopedAccess;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.data.LimitedPoolResource;
import io.hyperfoil.core.data.Queue;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.PathMetricSelector;
import io.hyperfoil.core.util.Unique;
import io.hyperfoil.function.SerializableBiFunction;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.handlers.Location;
import io.hyperfoil.http.steps.HttpRequestStepBuilder;

public class FetchResourceHandler implements Serializable, ResourceUtilizer {
   private final Access var;
   private final int maxResources;
   private final String sequence;
   private final int concurrency;
   private final Action onCompletion;
   private final Queue.Key queueKey;
   private final LimitedPoolResource.Key<Location> locationPoolKey;

   public FetchResourceHandler(Queue.Key queueKey, LimitedPoolResource.Key<Location> locationPoolKey, Access var, int maxResources, String sequence, int concurrency, Action onCompletion) {
      this.queueKey = queueKey;
      this.locationPoolKey = locationPoolKey;
      this.var = var;
      this.maxResources = maxResources;
      this.sequence = sequence;
      this.concurrency = concurrency;
      this.onCompletion = onCompletion;
   }

   public void before(Session session) {
      Queue queue = session.getResource(queueKey);
      queue.reset(session);
   }

   public void handle(Session session, CharSequence authority, CharSequence path) {
      Queue queue = session.getResource(queueKey);
      LimitedPoolResource<Location> locationPool = session.getResource(locationPoolKey);
      Location location = locationPool.acquire();
      location.authority = authority;
      location.path = path;
      queue.push(session, location);
   }

   public void after(Session session) {
      Queue queue = session.getResource(queueKey);
      queue.producerComplete(session);
   }

   @Override
   public void reserve(Session session) {
      var.declareObject(session);
      // If there are multiple concurrent requests all the data end up in single queue;
      // there's no way to set up different output var so merging them is the only useful behaviour.
      if (!var.isSet(session)) {
         var.setObject(session, ObjectVar.newArray(session, concurrency));
      }
      session.declareResource(queueKey, () -> new Queue(var, maxResources, concurrency, sequence, onCompletion), true);
      session.declareResource(locationPoolKey, () -> LimitedPoolResource.create(maxResources, Location.class, Location::new), true);
      ResourceUtilizer.reserve(session, onCompletion);
   }

   /**
    * Automates download of embedded resources.
    */
   public static class Builder implements BuilderBase<Builder> {
      private SerializableBiFunction<String, String, String> metricSelector;
      private int maxResources;
      private int concurrency = 8;
      private Action.Builder onCompletion;

      private Queue.Key queueKey;
      private LimitedPoolResource.Key<Location> locationPoolKey;
      private Access varAccess;
      private String sequenceName;

      public Builder() {
      }

      /**
       * Maximum number of resources that can be fetched.
       *
       * @param maxResources Max resources.
       * @return Self.
       */
      public Builder maxResources(int maxResources) {
         this.maxResources = maxResources;
         return this;
      }

      /**
       * Maximum number of resources fetched concurrently. Default is 8.
       *
       * @param concurrency Max concurrently fetched resources.
       * @return Self.
       */
      public Builder concurrency(int concurrency) {
         this.concurrency = concurrency;
         return this;
      }

      /**
       * Metrics selector for downloaded resources.
       *
       * @return Builder.
       */
      public PathMetricSelector metric() {
         PathMetricSelector metricSelector = new PathMetricSelector();
         metric(metricSelector);
         return metricSelector;
      }

      public Builder metric(SerializableBiFunction<String, String, String> metricSelector) {
         if (this.metricSelector != null) {
            throw new BenchmarkDefinitionException("Metric already set!");
         }
         this.metricSelector = metricSelector;
         return this;
      }

      /**
       * Action performed when the download of all resources completes.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Action.Builder> onCompletion() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, this::onCompletion);
      }

      public Builder onCompletion(Action.Builder onCompletion) {
         this.onCompletion = onCompletion;
         return this;
      }

      public void prepareBuild() {
         queueKey = new Queue.Key();
         locationPoolKey = new LimitedPoolResource.Key<>();

         Locator locator = Locator.current();
         sequenceName = String.format("%s_fetchResources_%08x", locator.sequence().name(), ThreadLocalRandom.current().nextInt());
         Unique locationVar = new Unique();
         varAccess = SessionFactory.access(locationVar);

         // We'll keep the request synchronous to keep the session running while the resources are fetched
         // even if the benchmark did not specify any completion action.
         HttpRequestStepBuilder requestBuilder = new HttpRequestStepBuilder().sync(true).method(HttpMethod.GET);
         requestBuilder.path(() -> new Location.GetPath(sequenceScopedAccess(locationVar)));
         requestBuilder.authority(() -> new Location.GetAuthority(sequenceScopedAccess(locationVar)));
         if (metricSelector != null) {
            requestBuilder.metric(metricSelector);
         } else {
            // Rather than using auto-generated sequence name we'll use the full path
            requestBuilder.metric(new AuthorityAndPathMetric());
         }
         SequenceBuilder sequence = locator.scenario().sequence(sequenceName).concurrency(concurrency);
         sequence.stepBuilder(requestBuilder);
         var myQueueKey = queueKey; // prevent capturing self reference
         var myPoolKey = locationPoolKey;
         requestBuilder.handler().onCompletion(() -> new Location.Complete<>(myPoolKey, myQueueKey, sequenceScopedAccess(locationVar)));
         // As we're preparing build, the list of sequences-to-be-prepared is already final and we need to prepare
         // this one manually
         sequence.prepareBuild();
      }

      public FetchResourceHandler build() {
         if (maxResources <= 0) {
            throw new BenchmarkDefinitionException("Maximum size for queue must be set!");
         }
         Action onCompletion = this.onCompletion == null ? null : this.onCompletion.build();
         return new FetchResourceHandler(queueKey, locationPoolKey, varAccess, maxResources, sequenceName, concurrency, onCompletion);
      }
   }

   public static class AuthorityAndPathMetric implements SerializableBiFunction<String, String, String> {
      @Override
      public String apply(String authority, String path) {
         return authority == null ? path : authority + path;
      }
   }
}

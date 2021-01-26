package io.hyperfoil.core.handlers.html;

import java.util.concurrent.ThreadLocalRandom;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.generators.StringGeneratorImplBuilder;
import io.hyperfoil.core.handlers.QueueProcessor;
import io.hyperfoil.core.steps.HttpRequestStep;
import io.hyperfoil.core.steps.PathMetricSelector;
import io.hyperfoil.function.SerializableBiFunction;

/**
 * Automates download of embedded resources.
 */
public class FetchResourceBuilder implements BuilderBase<FetchResourceBuilder> {
   private SerializableBiFunction<String, String, String> metricSelector;
   private QueueProcessor.Builder queue = new QueueProcessor.Builder().concurrency(8);

   FetchResourceBuilder() {
   }

   /**
    * Maximum number of resources that can be fetched.
    *
    * @param maxResources Max resources.
    * @return Self.
    */
   public FetchResourceBuilder maxResources(int maxResources) {
      queue.maxSize(maxResources);
      return this;
   }

   /**
    * Maximum number of resources fetched concurrently. Default is 8.
    *
    * @param concurrency Max concurrently fetched resources.
    * @return Self.
    */
   public FetchResourceBuilder concurrency(int concurrency) {
      queue.concurrency(concurrency);
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

   public FetchResourceBuilder metric(SerializableBiFunction<String, String, String> metricSelector) {
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
      return queue.onCompletion();
   }

   public FetchResourceBuilder onCompletion(Action.Builder a) {
      queue.onCompletion(a);
      return this;
   }

   public void prepareBuild() {
      Locator locator = Locator.current();
      String generatedSequenceName = String.format("%s_fetchResources_%08x",
            locator.sequence().name(), ThreadLocalRandom.current().nextInt());
      String downloadUrlVar = generatedSequenceName + "_url";

      // We'll keep the request synchronous to keep the session running while the resources are fetched
      // even if the benchmark did not specify any completion action.
      HttpRequestStep.Builder requestBuilder = new HttpRequestStep.Builder().sync(true).method(HttpMethod.GET);
      requestBuilder.path(
            new StringGeneratorImplBuilder<>(requestBuilder, false).fromVar(downloadUrlVar + "[.]"));
      if (metricSelector != null) {
         requestBuilder.metric(metricSelector);
      } else {
         // Rather than using auto-generated sequence name we'll use the full path
         requestBuilder.metric((authority, path) -> authority != null ? authority + path : path);
      }
      SequenceBuilder sequence = locator.scenario().sequence(generatedSequenceName);
      sequence.stepBuilder(requestBuilder);
      queue.var(downloadUrlVar).sequence(sequence, requestBuilder.handler()::onCompletion);
      // As we're preparing build, the list of sequences-to-be-prepared is already final and we need to prepare
      // this one manually
      queue.prepareBuild();
   }

   public Processor build() {
      return queue.build(false);
   }
}

package io.hyperfoil.hotrod.steps;

import java.util.Arrays;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.core.generators.StringGeneratorImplBuilder;
import io.hyperfoil.core.metric.MetricSelector;
import io.hyperfoil.core.metric.PathMetricSelector;
import io.hyperfoil.core.metric.ProvidedMetricSelector;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.hotrod.api.HotRodOperation;
import io.hyperfoil.hotrod.resource.HotRodResource;

/**
 * Issues a HotRod request and registers handlers for the response.
 */
@MetaInfServices(StepBuilder.class)
@Name("hotrodRequest")
public class HotRodRequestBuilder extends BaseStepBuilder<HotRodRequestBuilder> {

   private HotRodOperationBuilder operation;
   private StringGeneratorBuilder cacheName;
   private MetricSelector metricSelector;
   private StringGeneratorBuilder key;
   private StringGeneratorBuilder value;

   @Override
   public void prepareBuild() {
      if (metricSelector == null) {
         String sequenceName = Locator.current().sequence().name();
         metricSelector = new ProvidedMetricSelector(sequenceName);
      }
   }

   @Override
   public List<Step> build() {
      int stepId = StatisticsStep.nextId();
      HotRodResource.Key key = new HotRodResource.Key();
      SerializableFunction<Session, String> keyGenerator = this.key != null ? this.key.build() : null;
      SerializableFunction<Session, String> valueGenerator = this.value != null ? this.value.build() : null;
      HotRodRequestStep step = new HotRodRequestStep(stepId, key, operation.build(), cacheName.build(), metricSelector,
            keyGenerator, valueGenerator);
      HotRodResponseStep secondHotRodStep = new HotRodResponseStep(key);
      return Arrays.asList(step, secondHotRodStep);
   }

   /**
    * Requests statistics will use this metric name.
    *
    * @param name Metric name.
    * @return Self.
    */
   public HotRodRequestBuilder metric(String name) {
      return metric(new ProvidedMetricSelector(name));
   }

   public HotRodRequestBuilder metric(ProvidedMetricSelector selector) {
      this.metricSelector = selector;
      return this;
   }

   /**
    * Allows categorizing request statistics into metrics based on the request path.
    *
    * @return Builder.
    */
   public PathMetricSelector metric() {
      PathMetricSelector selector = new PathMetricSelector();
      this.metricSelector = selector;
      return selector;
   }

   public StringGeneratorImplBuilder<HotRodRequestBuilder> cacheName() {
      StringGeneratorImplBuilder<HotRodRequestBuilder> builder = new StringGeneratorImplBuilder<>(this, false);
      cacheName(builder);
      return builder;
   }

   public HotRodRequestBuilder cacheName(StringGeneratorBuilder builder) {
      if (this.cacheName != null) {
         throw new BenchmarkDefinitionException("CacheName generator already set.");
      }
      this.cacheName = builder;
      return this;
   }

   /**
    * Name of the cache used for the operation. This can be a <a href="https://hyperfoil.io/userguide/benchmark/variables.html#string-interpolation">pattern</a>.
    *
    * @param pattern Cache name (<code>my-cache</code>) or a pattern (<code>cache-${index}</code>).
    * @return Self.
    */
   public HotRodRequestBuilder cacheName(String pattern) {
      return cacheName().pattern(pattern).end();
   }

   public HotRodRequestBuilder operation(HotRodOperation operation) {
      return operation(() -> new HotRodOperationBuilder.Provided(operation));
   }

   public HotRodRequestBuilder operation(HotRodOperationBuilder operation) {
      this.operation = operation;
      return this;
   }

   public StringGeneratorImplBuilder<HotRodRequestBuilder> key() {
      StringGeneratorImplBuilder<HotRodRequestBuilder> builder = new StringGeneratorImplBuilder<>(this, false);
      key(builder);
      return builder;
   }

   public HotRodRequestBuilder key(StringGeneratorBuilder builder) {
      if (this.key != null) {
         throw new BenchmarkDefinitionException("Key generator already set.");
      }
      this.key = builder;
      return this;
   }

   /**
    * Key used for the operation. This can be a <a href="https://hyperfoil.io/userguide/benchmark/variables.html#string-interpolation">pattern</a>.
    *
    * @param pattern The key.
    * @return Self.
    */
   public HotRodRequestBuilder key(String pattern) {
      return key().pattern(pattern).end();
   }

   public StringGeneratorImplBuilder<HotRodRequestBuilder> value() {
      StringGeneratorImplBuilder<HotRodRequestBuilder> builder = new StringGeneratorImplBuilder<>(this, false);
      value(builder);
      return builder;
   }

   public HotRodRequestBuilder value(StringGeneratorBuilder builder) {
      if (this.value != null) {
         throw new BenchmarkDefinitionException("Value generator already set.");
      }
      this.value = builder;
      return this;
   }

   /**
    * Value for the operation. This can be a <a href="https://hyperfoil.io/userguide/benchmark/variables.html#string-interpolation">pattern</a>.
    *
    * @param pattern The value.
    * @return Self.
    */
   public HotRodRequestBuilder value(String pattern) {
      return value().pattern(pattern).end();
   }

   /**
    * Adds or overrides each specified entry in the remote cache.
    *
    * @param cacheName Name of cache to put data. This can be a <a href="https://hyperfoil.io/userguide/benchmark/variables.html#string-interpolation">pattern</a>.
    * @return
    */
   public HotRodRequestBuilder put(String cacheName) {
      return operation(HotRodOperation.PUT).cacheName(cacheName);
   }

   /**
    * Get specified entry in the remote cache.
    *
    * @param cacheName Name of cache to put data. This can be a <a href="https://hyperfoil.io/userguide/benchmark/variables.html#string-interpolation">pattern</a>.
    * @return
    */
   public HotRodRequestBuilder get(String cacheName) {
      return operation(HotRodOperation.GET).cacheName(cacheName);
   }
}

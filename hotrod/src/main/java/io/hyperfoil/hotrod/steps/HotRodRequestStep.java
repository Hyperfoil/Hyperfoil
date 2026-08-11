package io.hyperfoil.hotrod.steps;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.infinispan.client.hotrod.exceptions.HotRodTimeoutException;

import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.config.StartTimeSource;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.metric.MetricSelector;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.hotrod.api.HotRodOperation;
import io.hyperfoil.hotrod.api.HotRodRemoteCachePool;
import io.hyperfoil.hotrod.connection.HotRodRemoteCachePoolImpl;
import io.hyperfoil.hotrod.resource.HotRodResource;

public class HotRodRequestStep extends StatisticsStep implements ResourceUtilizer, SLA.Provider, StartTimeSource {

   final HotRodResource.Key futureWrapperKey;
   final SerializableFunction<Session, HotRodOperation> operation;
   final SerializableFunction<Session, String> cacheName;
   final MetricSelector metricSelector;
   final SerializableFunction<Session, String> keyGenerator;
   final SerializableFunction<Session, String> valueGenerator;
   final boolean useSessionStartTime;

   protected HotRodRequestStep(int id, HotRodResource.Key futureWrapperKey,
         SerializableFunction<Session, HotRodOperation> operation,
         SerializableFunction<Session, String> cacheName,
         MetricSelector metricSelector,
         SerializableFunction<Session, String> keyGenerator,
         SerializableFunction<Session, String> valueGenerator,
         boolean useSessionStartTime) {
      super(id);
      this.futureWrapperKey = futureWrapperKey;
      this.operation = operation;
      this.cacheName = cacheName;
      this.metricSelector = metricSelector;
      this.keyGenerator = keyGenerator;
      this.valueGenerator = valueGenerator;
      this.useSessionStartTime = useSessionStartTime;
   }

   @Override
   public SLA[] sla() {
      return new SLA[0];
   }

   @Override
   public boolean invoke(Session session) {

      String cacheName = this.cacheName.apply(session);
      HotRodOperation operation = this.operation.apply(session);
      Object key = keyGenerator.apply(session);
      Object value = null;
      if (valueGenerator != null) {
         value = valueGenerator.apply(session);
      }
      HotRodRemoteCachePool pool = HotRodRemoteCachePool.get(session);
      HotRodRemoteCachePoolImpl.RemoteCacheWithoutToString remoteCache = pool.getRemoteCache(cacheName);
      String metric = metricSelector.apply(null, cacheName);
      Statistics statistics = session.statistics(id(), metric);

      HotRodResource resource = session.getResource(futureWrapperKey);

      this.createStartTimestamp(session, this.useSessionStartTime, resource.timestamps);

      CompletableFuture future;
      if (HotRodOperation.PUT.equals(operation)) {
         future = remoteCache.putAsync(key, value);
      } else if (HotRodOperation.GET.equals(operation)) {
         future = remoteCache.getAsync(key);
      } else {
         throw new IllegalArgumentException(String.format("HotRodOperation %s not implemented", operation));
      }

      resource.set(future);
      statistics.incrementRequests(this, session);

      future.exceptionally(t -> {
         trackResponseError(session, metric, t);
         return null;
      });
      future.thenRun(() -> {
         trackResponseSuccess(session, metric);
         assert session.executor().inEventLoop();
         session.proceed();
      });
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(futureWrapperKey, HotRodResource::new);
   }

   private void trackResponseError(Session session, String metric, Object ex) {
      assert session.executor().inEventLoop();
      Statistics statistics = session.statistics(id(), metric);
      HotRodResource resource = session.getResource(futureWrapperKey);
      if (ex instanceof TimeoutException || ex instanceof HotRodTimeoutException) {
         statistics.incrementTimeouts(this, session);
      } else {
         statistics.incrementConnectionErrors(this, session);
      }
      session.stop();
   }

   private void trackResponseSuccess(Session session, String metric) {
      assert session.executor().inEventLoop();
      HotRodResource resource = session.getResource(futureWrapperKey);
      long startTimestampNanos = resource.getStartTimestampNanos();
      Statistics statistics = session.statistics(id(), metric);
      statistics.recordResponse(this, System.nanoTime() - startTimestampNanos, session);
   }

   @Override
   public long getStartTimestampMillis(Session session) {
      assert session.executor().inEventLoop();
      HotRodResource resource = session.getResource(futureWrapperKey);
      return resource.getStartTimestampMillis();
   }

   @Override
   public long getStartTimestampNanos(Session session) {
      assert session.executor().inEventLoop();
      HotRodResource resource = session.getResource(futureWrapperKey);
      return resource.getStartTimestampNanos();
   }
}

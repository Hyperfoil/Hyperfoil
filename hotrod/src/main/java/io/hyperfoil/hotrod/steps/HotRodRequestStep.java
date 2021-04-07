package io.hyperfoil.hotrod.steps;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.infinispan.client.hotrod.exceptions.HotRodTimeoutException;

import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.builders.SLA;
import io.hyperfoil.core.metric.MetricSelector;
import io.hyperfoil.core.session.IntVar;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.hotrod.api.HotRodOperation;
import io.hyperfoil.hotrod.api.HotRodRemoteCachePool;
import io.hyperfoil.hotrod.connection.HotRodRemoteCachePoolImpl;
import io.hyperfoil.hotrod.resource.HotRodResource;

public class HotRodRequestStep extends StatisticsStep implements ResourceUtilizer, SLA.Provider {

   final HotRodResource.Key futureWrapperKey;
   final SerializableFunction<Session, HotRodOperation> operation;
   final SerializableFunction<Session, String> cacheName;
   final MetricSelector metricSelector;
   final SerializableFunction<Session, String> keyGenerator;
   final SerializableFunction<Session, String> valueGenerator;

   protected HotRodRequestStep(int id, HotRodResource.Key futureWrapperKey,
                               SerializableFunction<Session, HotRodOperation> operation,
                               SerializableFunction<Session, String> cacheName,
                               MetricSelector metricSelector,
                               SerializableFunction<Session, String> keyGenerator,
                               SerializableFunction<Session, String> valueGenerator) {
      super(id);
      this.futureWrapperKey = futureWrapperKey;
      this.operation = operation;
      this.cacheName = cacheName;
      this.metricSelector = metricSelector;
      this.keyGenerator = keyGenerator;
      this.valueGenerator = valueGenerator;
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

      long startTimestampMs = System.currentTimeMillis();
      long startTimestampNanos = System.nanoTime();
      CompletableFuture future;
      if (HotRodOperation.PUT.equals(operation)) {
         future = remoteCache.putAsync(key, value);
      } else if (HotRodOperation.GET.equals(operation)) {
         future = remoteCache.getAsync(key);
      } else {
         throw new IllegalArgumentException(String.format("HotRodOperation %s not implemented", operation));
      }
      statistics.incrementRequests(startTimestampMs);
      future.exceptionally(t -> {
         trackResponseError(session, metric, t);
         return null;
      });
      future.thenRun(() -> {
         trackResponseSuccess(session, metric);
         assert session.executor().inEventLoop();
         session.proceed();
      });
      session.getResource(futureWrapperKey).set(future, startTimestampNanos, startTimestampMs);

      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(futureWrapperKey, HotRodResource::new);
   }

   private void trackResponseError(Session session, String metric, Object ex) {
      Statistics statistics = session.statistics(id(), metric);
      if (ex instanceof TimeoutException || ex instanceof HotRodTimeoutException) {
         statistics.incrementTimeouts(System.currentTimeMillis());
      } else {
         statistics.incrementConnectionErrors(System.currentTimeMillis());
      }
      session.stop();
   }

   private void trackResponseSuccess(Session session, String metric) {
      HotRodResource resource = session.getResource(futureWrapperKey);
      long startTimestampMillis = resource.getStartTimestampMillis();
      long startTimestampNanos = resource.getStartTimestampNanos();
      long endTimestampNanos = System.nanoTime();

      Statistics statistics = session.statistics(id(), metric);
      statistics.recordResponse(startTimestampMillis, endTimestampNanos - startTimestampNanos);
   }

   private Object getValue(Session.Var sessionVar) {
      if (sessionVar instanceof ObjectVar) {
         return ((ObjectVar) sessionVar).get();
      } else if (sessionVar instanceof IntVar) {
         return ((IntVar) sessionVar).get();
      } else {
         throw new IllegalStateException(String.format("%s not implemented", sessionVar.getClass().getSimpleName()));
      }
   }
}

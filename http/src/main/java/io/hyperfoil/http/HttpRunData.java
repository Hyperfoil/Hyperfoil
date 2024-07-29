package io.hyperfoil.http;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.SSLException;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.api.PluginRunData;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.http.api.HttpCache;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpDestinationTable;
import io.hyperfoil.http.config.ConnectionStrategy;
import io.hyperfoil.http.config.Http;
import io.hyperfoil.http.config.HttpPluginConfig;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.http.connection.HttpDestinationTableImpl;
import io.hyperfoil.http.connection.SessionConnectionPool;
import io.netty.channel.EventLoop;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class HttpRunData implements PluginRunData {
   private final HttpPluginConfig plugin;
   private final HttpDestinationTableImpl[] destinations;
   private final Map<String, HttpClientPool> clientPools = new HashMap<>();
   private final boolean hasSessionPools;
   private final boolean hasHttpCacheEnabled;

   public HttpRunData(Benchmark benchmark, EventLoop[] executors, int agentId) {
      plugin = benchmark.plugin(HttpPluginConfig.class);
      // either all http configs disable the cache or keep it enabled
      hasHttpCacheEnabled = plugin.http().values().stream().anyMatch(Http::enableHttpCache);
      hasSessionPools = plugin.http().values().stream()
            .anyMatch(http -> http.connectionStrategy() != ConnectionStrategy.SHARED_POOL);
      @SuppressWarnings("unchecked")
      Map<String, HttpConnectionPool>[] connectionPools = new Map[executors.length];
      destinations = new HttpDestinationTableImpl[executors.length];
      for (Map.Entry<String, Http> http : plugin.http().entrySet()) {
         try {
            HttpClientPool httpClientPool = new HttpClientPoolImpl(http.getValue(), executors, benchmark, agentId);
            clientPools.put(http.getKey(), httpClientPool);
            if (http.getValue().isDefault()) {
               clientPools.put(null, httpClientPool);
            }

            for (int executorId = 0; executorId < executors.length; ++executorId) {
               HttpConnectionPool httpConnectionPool = httpClientPool.connectionPool(executors[executorId]);
               Map<String, HttpConnectionPool> pools = connectionPools[executorId];
               if (pools == null) {
                  connectionPools[executorId] = pools = new HashMap<>();
               }
               pools.put(http.getKey(), httpConnectionPool);
               if (http.getValue().isDefault()) {
                  pools.put(null, httpConnectionPool);
               }
            }
         } catch (SSLException e) {
            throw new IllegalStateException(
                  "Failed creating connection pool to " + http.getValue().host() + ":" + http.getValue().port(), e);
         }
      }
      for (int executorId = 0; executorId < connectionPools.length; executorId++) {
         Map<String, HttpConnectionPool> pools = connectionPools[executorId];
         destinations[executorId] = new HttpDestinationTableImpl(pools);
      }
   }

   public static void initForTesting(Session session) {
      initForTesting(session, Clock.systemDefaultZone(), true);
   }

   public static void initForTesting(Session session, Clock clock, boolean cacheEnabled) {
      Scenario dummyScenario = new Scenario(new Sequence[0], new Sequence[0], 16, 16);
      session.declareSingletonResource(HttpDestinationTable.KEY, new HttpDestinationTableImpl(Collections.emptyMap()));
      if (cacheEnabled) {
         session.declareSingletonResource(HttpCache.KEY, new HttpCacheImpl(clock));
      }
      session.declareSingletonResource(HttpRequestPool.KEY, new HttpRequestPool(dummyScenario, session, cacheEnabled));
   }

   @Override
   public void initSession(Session session, int executorId, Scenario scenario, Clock clock) {
      HttpDestinationTable destinations = this.destinations[executorId];
      if (hasSessionPools) {
         destinations = new HttpDestinationTableImpl(destinations,
               pool -> {
                  ConnectionStrategy strategy = pool.clientPool().config().connectionStrategy();
                  switch (strategy) {
                     case SHARED_POOL:
                     case ALWAYS_NEW:
                        return pool;
                     case SESSION_POOLS:
                     case OPEN_ON_REQUEST:
                        return new SessionConnectionPool(pool, scenario.maxRequests());
                     default:
                        throw new IllegalStateException();
                  }
               });
      }
      session.declareSingletonResource(HttpDestinationTable.KEY, destinations);
      if (hasHttpCacheEnabled) {
         session.declareSingletonResource(HttpCache.KEY, new HttpCacheImpl(clock));
      }
      session.declareSingletonResource(HttpRequestPool.KEY, new HttpRequestPool(scenario, session, hasHttpCacheEnabled));
   }

   @Override
   public void openConnections(Function<Callable<Void>, Future<Void>> blockingHandler,
         Consumer<Future<Void>> promiseCollector) {
      for (Map.Entry<String, HttpClientPool> entry : clientPools.entrySet()) {
         // default client pool is initialized by name
         if (entry.getKey() != null) {
            Promise<Void> promise = Promise.promise();
            promiseCollector.accept(promise.future());
            entry.getValue().start(promise);
         }
      }
   }

   @Override
   public void listConnections(Consumer<String> connectionCollector) {
      // Connection pools should be accessed only from the executor, but since we're only publishing stats...
      for (HttpDestinationTableImpl destinations : destinations) {
         for (Map.Entry<String, HttpConnectionPool> entry : destinations.iterable()) {
            if (entry.getKey() == null) {
               // Ignore default pool: it's there twice
               continue;
            }
            HttpConnectionPool pool = entry.getValue();
            Collection<? extends HttpConnection> connections = pool.connections();
            Map<String, AtomicInteger> byType = new HashMap<>();
            int available = 0;
            int inFlight = 0;
            for (HttpConnection conn : connections) {
               if (conn.isAvailable()) {
                  available++;
               }
               inFlight += conn.inFlight();
               byType.computeIfAbsent(conn.getClass().getSimpleName() + (conn.isSecure() ? "(SSL)" : ""),
                     k -> new AtomicInteger()).incrementAndGet();
            }
            connectionCollector
                  .accept(String.format("%s: %d/%d available, %d in-flight requests, %d waiting sessions (estimate), types: %s",
                        entry.getKey(), available, connections.size(), inFlight, pool.waitingSessions(), byType));
         }
      }
   }

   @Override
   public void visitConnectionStats(ConnectionStatsConsumer consumer) {
      for (var entry : clientPools.entrySet()) {
         // default pool is in the map twice
         if (entry.getKey() != null) {
            entry.getValue().visitConnectionStats(consumer);
         }
      }
   }

   @Override
   public void shutdown() {
      for (HttpClientPool pool : clientPools.values()) {
         pool.shutdown();
      }
   }
}

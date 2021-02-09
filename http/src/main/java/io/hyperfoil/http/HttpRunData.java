package io.hyperfoil.http;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.net.ssl.SSLException;

import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.api.PluginRunData;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.http.api.HttpCache;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpDestinationTable;
import io.hyperfoil.http.config.Http;
import io.hyperfoil.http.config.HttpPluginConfig;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.http.connection.HttpDestinationTableImpl;
import io.hyperfoil.http.connection.PrivateConnectionPool;
import io.netty.channel.EventLoop;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class HttpRunData implements PluginRunData {
   private final HttpPluginConfig plugin;
   private final HttpDestinationTableImpl[] destinations;
   private final Map<String, HttpClientPool> clientPools = new HashMap<>();
   private final boolean hasPrivatePools;

   public HttpRunData(Benchmark benchmark, EventLoop[] executors, int agentId) {
      plugin = benchmark.plugin(HttpPluginConfig.class);
      hasPrivatePools = plugin.http().values().stream().anyMatch(Http::privatePools);
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
            throw new IllegalStateException("Failed creating connection pool to " + http.getValue().host() + ":" + http.getValue().port(), e);
         }
      }
      for (int executorId = 0; executorId < connectionPools.length; executorId++) {
         Map<String, HttpConnectionPool> pools = connectionPools[executorId];
         destinations[executorId] = new HttpDestinationTableImpl(pools);
      }
   }

   public static void initForTesting(Session session) {
      initForTesting(session, Clock.systemDefaultZone());
   }

   public static void initForTesting(Session session, Clock clock) {
      Scenario dummyScenario = new Scenario(new Sequence[0], new Sequence[0], null, null, 16, 16);
      session.declareSingletonResource(HttpDestinationTable.KEY, new HttpDestinationTableImpl(Collections.emptyMap()));
      session.declareSingletonResource(HttpCache.KEY, new HttpCacheImpl(clock));
      session.declareSingletonResource(HttpRequestPool.KEY, new HttpRequestPool(dummyScenario, session));
   }

   @Override
   public void initSession(Session session, int executorId, Scenario scenario, Clock clock) {
      HttpDestinationTable destinations = this.destinations[executorId];
      if (hasPrivatePools) {
         destinations = new HttpDestinationTableImpl(destinations,
               pool -> pool.clientPool().config().privatePools() ? new PrivateConnectionPool(pool) : pool);
      }
      session.declareSingletonResource(HttpDestinationTable.KEY, destinations);
      session.declareSingletonResource(HttpCache.KEY, new HttpCacheImpl(clock));
      session.declareSingletonResource(HttpRequestPool.KEY, new HttpRequestPool(scenario, session));
   }

   @Override
   public void openConnections(Consumer<Future<Void>> promiseCollector) {
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
               byType.computeIfAbsent(conn.getClass().getSimpleName() + (conn.isSecure() ? "(SSL)" : ""), k -> new AtomicInteger()).incrementAndGet();
            }
            connectionCollector.accept(String.format("%s: %d/%d available, %d in-flight requests, %d waiting sessions (estimate), types: %s", entry.getKey(), available, connections.size(), inFlight, pool.waitingSessions(), byType));
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

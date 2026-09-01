package io.hyperfoil.grpc;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.protobuf.Descriptors;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.api.PluginRunData;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.grpc.api.GrpcClientConnectionsPool;
import io.hyperfoil.grpc.api.GrpcProtoDescriptions;
import io.hyperfoil.grpc.config.GrpcPluginConfig;
import io.hyperfoil.grpc.connection.GrpcClientConnectionsPoolImpl;
import io.hyperfoil.grpc.proto.GrpcProtoDescriptionsImpl;
import io.netty.channel.EventLoop;
import io.vertx.core.Future;

public class GrpcRunData implements PluginRunData {

   private GrpcPluginConfig plugin;
   private GrpcClientConnectionsPool[] clientPools;
   private GrpcProtoDescriptions protoDescriptions;

   public GrpcRunData(Benchmark benchmark, EventLoop[] executors) {
      // TODO distribuite the shared connections among executors, per authority: check HttpClientPoolImpl::new
      plugin = benchmark.plugin(GrpcPluginConfig.class);
      clientPools = new GrpcClientConnectionsPool[executors.length];
      final Map<String, int[]> connectionsByAuthority = new HashMap<>();
      final Map<String, Descriptors.FileDescriptor> protoByAuthority = new HashMap<>();
      String defaultAuthority = null;
      for (var grpcEntry : plugin.grpcByAuthority().entrySet()) {
         var authority = grpcEntry.getKey();
         if (grpcEntry.getValue().isDefault()) {
            defaultAuthority = authority;
         }
         var protoConfig = grpcEntry.getValue().protoConfig();
         protoByAuthority.put(authority, protoConfig.toProtoFileDescriptor());
         var availableConnections = connectionsByAuthority.get(authority);
         if (availableConnections == null) {
            final int sharedConnections = grpcEntry.getValue().sharedConnections();
            if (sharedConnections != -1) {
               availableConnections = new int[executors.length];
               connectionsByAuthority.put(authority, availableConnections);
               int connectionsPerExecutor = sharedConnections / executors.length;
               // TODO what if is 0? it means that there won't be enough workers with their connections
               for (int executorId = 0; executorId < executors.length; executorId++) {
                  availableConnections[executorId] = connectionsPerExecutor;
               }
               int remainingConnections = sharedConnections % executors.length;
               if (remainingConnections > 0) {
                  availableConnections[executors.length - 1] = remainingConnections;
               }
            }
         }
      }
      protoDescriptions = new GrpcProtoDescriptionsImpl(defaultAuthority, protoByAuthority);
      for (int executorId = 0; executorId < executors.length; executorId++) {
         Map<String, Integer> eventLoopConnectionsByAuthority = new HashMap<>();
         for (var entry : connectionsByAuthority.entrySet()) {
            // move on if there are no connections for this executor
            if (entry.getValue() == null) {
               continue;
            }
            eventLoopConnectionsByAuthority.put(entry.getKey(), entry.getValue()[executorId]);
         }
         clientPools[executorId] = new GrpcClientConnectionsPoolImpl(defaultAuthority, plugin.grpcByAuthority(),
               eventLoopConnectionsByAuthority, executors[executorId]);
      }
   }

   @Override
   public void initSession(Session session, int executorId, Scenario scenario, Clock clock) {
      session.declareSingletonResource(GrpcClientConnectionsPool.KEY, clientPools[executorId]);
      session.declareSingletonResource(GrpcProtoDescriptions.KEY, protoDescriptions);
   }

   @Override
   public void openConnections(Function<Callable<Void>, Future<Void>> blockingHandler,
         Consumer<Future<Void>> promiseCollector) {
      for (GrpcClientConnectionsPool clientPool : clientPools) {
         clientPool.openConnections(promiseCollector::accept);
      }
   }

   @Override
   public void listConnections(Consumer<String> connectionCollector) {
      // TODO
   }

   @Override
   public void visitConnectionStats(ConnectionStatsConsumer consumer) {
      // TODO
   }

   @Override
   public void shutdown() {
      for (GrpcClientConnectionsPool clientPool : clientPools) {
         clientPool.shutdown();
      }
   }
}

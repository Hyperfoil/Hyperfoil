package io.hyperfoil.grpc.api;

import java.util.function.Consumer;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.grpc.connection.VertxGrpcClient;
import io.vertx.core.Future;

/**
 * This pool holds all gRPC connections for a single executor.
 */
public interface GrpcClientConnectionsPool extends Session.Resource {

   interface GrpcClientConnections {
      VertxGrpcClient acquire(Session session);

   }

   Session.ResourceKey<GrpcClientConnectionsPool> KEY = new Session.ResourceKey<>() {
   };

   GrpcClientConnections connectionsFor(String authority);

   void openConnections(Consumer<Future<Void>> promiseCollector);

   void shutdown();
}

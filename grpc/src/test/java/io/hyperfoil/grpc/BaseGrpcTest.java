package io.hyperfoil.grpc;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.core.session.BaseScenarioTest;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class BaseGrpcTest extends BaseScenarioTest {

   protected Vertx vertx;
   protected VertxTestContext ctx;

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      super.before();
      this.vertx = vertx;
      this.ctx = ctx;
      ctx.completeNow();
   }

   public HttpServer startGrpcServiceWith(ServiceName serviceName, String methodName,
         BiConsumer<GrpcServerRequest<Buffer, Buffer>, Buffer> onReceivedRequest) {
      GrpcServer grpcServer = GrpcServer.server(vertx);
      HttpServer httpServer = vertx.createHttpServer()
            .requestHandler(grpcServer);
      grpcServer.callHandler(request -> {
         if (sameMethod(serviceName, methodName, request)) {
            request.handler(protoHelloBuffer -> onReceivedRequest.accept(request, protoHelloBuffer));
         } else {
            request.response()
                  .status(GrpcStatus.NOT_FOUND)
                  .end();
         }
      });
      return httpServer;
   }

   private static boolean sameMethod(ServiceName serviceName, String methodName, GrpcServerRequest<Buffer, Buffer> request) {
      // TODO open a PR to vertx-grpc to fix both method name and serviceName equality!!!
      return request.fullMethodName().equals(serviceName.fullyQualifiedName() + "/" + methodName);
   }

   static void blockingListen(HttpServer server, int port, String host) {
      server.listen(port, host).toCompletionStage().toCompletableFuture().join();
   }
}

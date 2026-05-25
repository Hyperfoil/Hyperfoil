package io.hyperfoil.grpc.resource;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.grpc.api.GrpcClientConnectionsPool;
import io.hyperfoil.grpc.connection.VertxGrpcClient;
import io.hyperfoil.grpc.steps.GrpcSendRequestStep;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;

public class GrpcRequestContext implements Session.Resource {
   public VertxGrpcClient acquiredClient;
   private GrpcClientRequest<Buffer, Buffer> request;
   public GrpcClientResponse<Buffer, Buffer> response;
   private String authority;
   /**
    * During the request preparation we have our own tracking of connections, in addition to the one of Vertx:
    * this means that if we disagree with the Vertx one (it could be a bug, really, or an implementation detail),
    * we need to both being able to correctly resume the session, which will block in the subsequent send step, or
    * not resuming it if there's no need ie the expected fast-path.
    */
   private boolean preparedRequest = false;
   public Buffer responseBuffer;
   private Session session;

   public void reset() {
      acquiredClient = null;
      request = null;
      response = null;
      authority = null;
      responseBuffer = null;
      preparedRequest = false;
      session = null;
   }

   public static boolean prepareRequest(Session session, String authority, GrpcRequestContext.KEY requestCtxKey) {
      GrpcClientConnectionsPool connectionsPool = session.getResource(GrpcClientConnectionsPool.KEY);
      GrpcClientConnectionsPool.GrpcClientConnections connections = connectionsPool.connectionsFor(authority);
      VertxGrpcClient grpcClient = connections.acquire(session);
      if (grpcClient == null) {
         return false;
      }
      var ctx = session.getResource(requestCtxKey);
      ctx.prepareRequest(session, grpcClient, authority);
      return true;
   }

   private void prepareRequest(Session session, VertxGrpcClient grpcClient, String authority) {
      acquiredClient = grpcClient;
      this.authority = authority;
      this.session = session;
      preparedRequest = false;
      grpcClient.request().onComplete(this::onPreparedRequest);
      preparedRequest = true;
   }

   public boolean sendRequest(GrpcSendRequestStep sendRequestStep) {
      if (request == null) {
         return false;
      }
      assert this.session == session;
      GrpcSendRequestStep.GrpcRequestData requestData = sendRequestStep.createGrpcRequestData(session, authority);
      var response = requestData.sendRequest(request, session);
      response.onComplete(this::onReceivedResponseHeader);
      return true;
   }

   private void onReceivedResponseHeader(AsyncResult<GrpcClientResponse<Buffer, Buffer>> ar) {
      if (ar.failed()) {
         try {
            session.fail(ar.cause());
         } finally {
            reset();
         }
      } else {
         this.response = ar.result();
         this.response.handler(this::onReceivedData);
      }
   }

   private void onReceivedData(Buffer data) {
      responseBuffer = data;
      session.proceed();
   }

   private void onPreparedRequest(AsyncResult<GrpcClientRequest<Buffer, Buffer>> ar) {
      if (ar.failed()) {
         acquiredClient.release();
         try {
            session.fail(new RuntimeException(ar.cause()));
         } finally {
            reset();
         }
      } else {
         request = ar.result();
         if (preparedRequest) {
            session.proceed();
         }
      }
   }

   public static class KEY implements Session.ResourceKey<GrpcRequestContext> {

   }
}

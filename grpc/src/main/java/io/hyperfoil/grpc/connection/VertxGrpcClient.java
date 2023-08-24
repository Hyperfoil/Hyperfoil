package io.hyperfoil.grpc.connection;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.impl.GrpcClientRequestImpl;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;

public final class VertxGrpcClient {
   private final HttpClient client;
   private final SocketAddress socketAddress;
   private final RequestOptions requestOptions;

   /**
    * TODO improve it: we shouldn't make it package private public
    */
   Runnable releaseHandler;

   public VertxGrpcClient(Vertx vertx, HttpClientOptions options) {
      if (options.isShared()) {
         throw new IllegalStateException("Shared connections are not supported");
      }
      if (options.getDefaultHost() == null || options.getDefaultPort() == -1) {
         throw new IllegalStateException("Default host/port must be specified");
      }
      socketAddress = SocketAddress.inetSocketAddress(options.getDefaultPort(), options.getDefaultHost());
      this.client = vertx.createHttpClient(new HttpClientOptions(options)
            .setProtocolVersion(HttpVersion.HTTP_2)
            // this is key to avoid pre allocating the connections will make them to be closed!
            .setPoolCleanerPeriod(0));
      this.requestOptions = new RequestOptions()
            .setMethod(HttpMethod.POST)
            .setServer(socketAddress);
   }

   public Future<GrpcClientRequest<Buffer, Buffer>> request() {
      return client.request(requestOptions)
            .map(request -> new GrpcClientRequestImpl<>(request, GrpcMessageEncoder.IDENTITY, GrpcMessageDecoder.IDENTITY));
   }

   public Future<Void> close() {
      return client.close();
   }

   /**
    * TODO improve it: we need a way to avoid mistakes on release it twice!
    */
   public void release() {
      releaseHandler.run();
   }
}

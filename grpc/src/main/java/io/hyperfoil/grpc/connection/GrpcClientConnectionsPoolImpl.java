package io.hyperfoil.grpc.connection;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.impl.EventLoopFactory;
import io.hyperfoil.grpc.api.GrpcClientConnectionsPool;
import io.hyperfoil.grpc.config.Grpc;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.SocketChannel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.spi.ExecutorServiceFactory;
import io.vertx.core.spi.transport.Transport;
import io.vertx.grpc.client.GrpcClientRequest;

public class GrpcClientConnectionsPoolImpl implements GrpcClientConnectionsPool {

   private static final class VertxGrpcSharedConnections implements GrpcClientConnectionsPool.GrpcClientConnections {
      private final ArrayDeque<Session> blockedSessions;
      private final int totalConnections;
      private int availableConnections;
      private final VertxGrpcClient grpcClient;

      private VertxGrpcSharedConnections(VertxGrpcClient grpcClient, int totalConnections) {
         this.totalConnections = totalConnections;
         this.grpcClient = grpcClient;
         this.availableConnections = totalConnections;
         this.blockedSessions = new ArrayDeque<>();
      }

      private void connect(Consumer<Future<Void>> promiseCollector) {
         List<AsyncResult<GrpcClientRequest<Buffer, Buffer>>> openedConnections = new ArrayList<>(totalConnections);
         Promise<Void>[] promises = new Promise[totalConnections];
         for (int i = 0; i < totalConnections; i++) {
            Promise<Void> openedConnection = Promise.promise();
            promises[i] = openedConnection;
            this.grpcClient.request().onComplete(event -> {
               openedConnections.add(event);
               // this is necessary to ensure that all connections are opened before starting the benchmark
               // TODO we should consider the maxStreams too? I believe so :"(
               if (openedConnections.size() == totalConnections) {
                  for (int c = 0; c < totalConnections; c++) {
                     var connection = openedConnections.get(c);
                     if (connection.succeeded()) {
                        // TODO another hack: cancel is run in the event loop
                        //      hence is going to immediately recycle the connection :/
                        connection.result().cancel();
                        promises[c].complete();
                     } else {
                        promises[c].fail(connection.cause());
                     }
                  }
               }
            });
            promiseCollector.accept(openedConnection.future());
         }
      }

      @Override
      public VertxGrpcClient acquire(Session session) {
         if (availableConnections == 0) {
            blockedSessions.add(session);
            return null;
         }
         availableConnections--;
         assert availableConnections >= 0;
         return grpcClient;
      }

      public Future<Void> close() {
         return grpcClient.close();
      }

      private void release() {
         availableConnections++;
         assert availableConnections <= totalConnections;
         Session blockedSession = blockedSessions.poll();
         if (blockedSession != null) {
            blockedSession.proceed();
         }
      }

      private void releaseAll() {
         availableConnections = totalConnections;
      }
   }

   private final Map<String, VertxGrpcSharedConnections> clientByAuthority;

   private final String defaultAuthority;

   private final EventLoop eventLoop;
   private final Vertx vertx;

   public GrpcClientConnectionsPoolImpl(String defaultAuthority, Map<String, Grpc> grpcByAuthority,
         Map<String, Integer> connectionsByAuthority, EventLoop executor) {
      this.defaultAuthority = defaultAuthority;
      eventLoop = executor;
      clientByAuthority = new HashMap<>(grpcByAuthority.size());
      this.vertx = new VertxBuilder(new VertxOptions()
            .setEventLoopPoolSize(1)
            // it's a limit of the vertx API :"(
            .setWorkerPoolSize(1))
            .findTransport(transportFor(executor))
            .executorServiceFactory(ExecutorServiceFactory.INSTANCE)
            .init()
            .vertx();
      for (Map.Entry<String, Grpc> entry : grpcByAuthority.entrySet()) {
         String authority = entry.getKey();
         Grpc grpc = entry.getValue();
         int sharedConnections = connectionsByAuthority.get(authority);
         VertxGrpcClient grpcClient = createGrpcClientOnEventLoop(grpc, vertx, sharedConnections);
         var grpcSharedConnections = new VertxGrpcSharedConnections(grpcClient, sharedConnections);
         grpcClient.releaseHandler = grpcSharedConnections::release;
         clientByAuthority.put(authority, grpcSharedConnections);
      }
   }

   private static Transport transportFor(EventLoop eventLoop) {
      return new Transport() {

         @Override
         public io.vertx.core.net.SocketAddress convert(SocketAddress address) {
            return Transport.super.convert(address);
         }

         @Override
         public EventLoopGroup eventLoopGroup(final int type, final int nThreads, final ThreadFactory threadFactory,
               final int ioRatio) {
            // TODO sub-optimimal: connections will be established from this event loop group!
            return eventLoop;
         }

         @Override
         public DatagramChannel datagramChannel() {
            Class<? extends DatagramChannel> channelClazz = EventLoopFactory.INSTANCE.datagramChannel();
            try {
               return channelClazz.getConstructor().newInstance();
            } catch (Throwable t) {
               throw new RuntimeException(t);
            }
         }

         @Override
         public DatagramChannel datagramChannel(final InternetProtocolFamily family) {
            Class<? extends DatagramChannel> channelClazz = EventLoopFactory.INSTANCE.datagramChannel();
            try {
               return channelClazz.getConstructor(InternetProtocolFamily.class).newInstance(family);
            } catch (Throwable t) {
               throw new RuntimeException(t);
            }
         }

         @Override
         public ChannelFactory<? extends Channel> channelFactory(final boolean domainSocket) {
            Class<? extends SocketChannel> socketChannel = EventLoopFactory.INSTANCE.socketChannel();
            // create a new instance using its class
            return (ChannelFactory<SocketChannel>) () -> {
               try {
                  return socketChannel.newInstance();
               } catch (InstantiationException | IllegalAccessException e) {
                  throw new RuntimeException(e);
               }
            };
         }

         @Override
         public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
            throw new UnsupportedOperationException("Not implemented");
         }
      };
   }

   private static VertxGrpcClient createGrpcClientOnEventLoop(Grpc grpc, Vertx vertx, int sharedConnections) {
      return new VertxGrpcClient(vertx, new HttpClientOptions()
            .setHttp2MaxPoolSize(sharedConnections)
            .setMaxPoolSize(sharedConnections)
            .setHttp2MultiplexingLimit(grpc.maxStreams())
            .setDefaultHost(grpc.host())
            .setDefaultPort(grpc.port()));
   }

   @Override
   public GrpcClientConnections connectionsFor(String authority) {
      if (authority == null) {
         authority = defaultAuthority;
      }
      return clientByAuthority.get(authority);
   }

   @Override
   public void openConnections(final Consumer<Future<Void>> promiseCollector) {
      for (VertxGrpcSharedConnections resource : clientByAuthority.values()) {
         resource.connect(promiseCollector);
      }
   }

   @Override
   public void shutdown() {
      clientByAuthority.values().forEach(resource -> {
         assert resource.availableConnections == resource.totalConnections;
         resource.close();
      });
   }

   @Override
   public void onSessionReset(Session session) {
      clientByAuthority.values().forEach(VertxGrpcSharedConnections::releaseAll);
   }

   @Override
   public void destroy() {
      clientByAuthority.clear();
   }
}

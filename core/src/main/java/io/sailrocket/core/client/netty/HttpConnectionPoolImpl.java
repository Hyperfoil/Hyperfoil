package io.sailrocket.core.client.netty;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.connection.HttpConnection;
import io.sailrocket.api.connection.HttpConnectionPool;
import io.sailrocket.api.connection.HttpRequestWriter;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.session.Session;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This instance is not thread-safe as it should be accessed only the {@link #executor()}.
 */
class HttpConnectionPoolImpl implements HttpConnectionPool {
   private static final Logger log = LoggerFactory.getLogger(HttpConnectionPoolImpl.class);

   private final HttpClientPoolImpl clientPool;
   private final ArrayList<HttpConnection> connections = new ArrayList<>();
   private final ArrayDeque<HttpConnection> available;
   private final int size;
   private final EventLoop eventLoop;
   private int count; // The estimated count : created + creating
   private Handler<AsyncResult<Void>> startedHandler;
   private boolean shutdown;
   private Deque<Session> waitingSessions = new ArrayDeque<>();

   HttpConnectionPoolImpl(HttpClientPoolImpl clientPool, EventLoop eventLoop, int size) {
      this.clientPool = clientPool;
      this.size = size;
      this.eventLoop = eventLoop;
      this.available = new ArrayDeque<>(size);
   }

   @Override
   public HttpClientPool clientPool() {
      return clientPool;
   }

   @Override
   public boolean request(Request request, HttpMethod method, Function<Session, String> pathGenerator, BiConsumer<Session, HttpRequestWriter>[] headerAppenders, BiFunction<Session, Connection, ByteBuf> bodyGenerator) {
      assert eventLoop.inEventLoop();
      HttpConnection connection = available.pollFirst();
      if (connection == null) {
         return false;
      }
      request.setRequestData(method);
      request.attach(connection);
      connection.request(request, method, pathGenerator, headerAppenders, bodyGenerator);
      // Move it to the back of the queue if it is still available (do not prefer it for subsequent requests)
      if (connection.isAvailable()) {
         available.addLast(connection);
      }
      return true;
   }

   @Override
   public void release(HttpConnection connection) {
      available.add(connection);
   }

   @Override
   public void registerWaitingSession(Session session) {
      waitingSessions.add(session);
   }

   @Override
   public int waitingSessions() {
      return waitingSessions.size();
   }

   @Override
   public EventExecutor executor() {
      return eventLoop;
   }

   @Override
   public void pulse() {
      Session session = waitingSessions.poll();
      log.trace("Pulse #{}", session == null ? "<none>" : session.uniqueId());
      if (session != null) {
         session.proceed();
      }
      // The session might not use the connection (e.g. when it's terminated) and call pulse() again
      // We don't want to activate all the sessions, though so we need to schedule another pulse
      if (!waitingSessions.isEmpty()) {
         executor().schedule(this::pulse, 1, TimeUnit.MILLISECONDS);
      }
   }

   @Override
   public Collection<HttpConnection> connections() {
      return connections;
   }

   private synchronized void checkCreateConnections(int retry) {
      assert eventLoop.inEventLoop();

      if (shutdown) {
         return;
      }
      //TODO:: configurable
      if (retry > 100) {
         // When the connection is not available we'll let sessions see & terminate if it's past the duration
         Handler<AsyncResult<Void>> handler = this.startedHandler;
         if (handler != null) {
            startedHandler = null;
            handler.handle(Future.failedFuture("Cannot connect to " + clientPool.authority));
         }
         pulse();
         throw new IllegalStateException();
      }
      if (count < size) {
         count++;
         clientPool.connect(this, (conn, err) -> {
            // at this moment we're in unknown thread
            if (err != null) {
               log.warn("Cannot create connection (retry {}): {}", retry, err.toString());
               // so we need to make sure that checkCreateConnections will be called in eventLoop
               eventLoop.execute(() -> {
                  count--;
                  checkCreateConnections(retry + 1);
               });
            } else {
               if (conn.context().executor() != eventLoop) {
                  log.trace("Connection created, re-registering...");
                  conn.context().channel().deregister().addListener(future -> {
                     if (future.isSuccess()) {
                        eventLoop.register(conn.context().channel()).addListener(regFuture -> {
                           if (regFuture.isSuccess()) {
                              connectionCreated(conn);
                           } else {
                              connectionFailed(retry, conn, regFuture.cause());
                           }
                        });
                     } else {
                        connectionFailed(retry, conn, future.cause());
                     }
                  });
               } else {
                  log.trace("Connection created, in correct event loop.");
                  assert eventLoop.inEventLoop();
                  connectionCreated(conn);
               }
            }
         });
         eventLoop.schedule(() -> checkCreateConnections(retry), 2, TimeUnit.MILLISECONDS);

      }
   }

   private void connectionCreated(HttpConnection conn) {
      assert eventLoop.inEventLoop();

      Handler<AsyncResult<Void>> handler = null;
      connections.add(conn);
      available.add(conn);
      if (count < size) {
         checkCreateConnections(0);
      } else {
         if (connections.size() == size) {
            handler = startedHandler;
            startedHandler = null;
         }
      }

      conn.context().channel().closeFuture().addListener(v -> {
         log.debug("Connection {} closed.", conn);
         count--;
         if (!shutdown) {
            connections.remove(conn);
            checkCreateConnections(0);
         }
      });

      if (handler != null) {
         handler.handle(Future.succeededFuture());
      }
      pulse();
   }

   private void connectionFailed(int retry, HttpConnection conn, Throwable cause) {
      log.warn("Failed to create a connection", cause);
      eventLoop.execute(() -> {
         --count;
         conn.context().close();
         checkCreateConnections(retry + 1);
      });
   }

   void start(Handler<AsyncResult<Void>> handler) {
      startedHandler = handler;
      eventLoop.execute(() -> checkCreateConnections(0));
   }

   void shutdown() {
      log.debug("Shutdown called");
      shutdown = true;
      if (eventLoop.isShutdown()) {
         // Already shutdown
         return;
      }
      eventLoop.execute(() -> {
         log.debug("Closing all connections");
         for (HttpConnection conn : connections) {
            conn.context().writeAndFlush(Unpooled.EMPTY_BUFFER);
            conn.context().close();
            conn.context().flush();
         }
      });
   }

}

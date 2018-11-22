package io.sailrocket.core.client.netty;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.sailrocket.api.connection.HttpConnectionPool;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.http.HttpRequest;
import io.sailrocket.api.session.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This instance is not thread-safe as it should be accessed only the {@link #executor()}.
 */
class HttpConnectionPoolImpl implements HttpConnectionPool {
   private static final Logger log = LoggerFactory.getLogger(HttpConnectionPoolImpl.class);

   private final HttpClientPoolImpl clientPool;
   private final ArrayList<HttpConnection> connections = new ArrayList<>();
   private final int size;
   private final EventLoop eventLoop;
   private int count; // The estimated count : created + creating
   private Runnable startedHandler;
   private boolean shutdown;
   private Deque<Session> waitingSessions = new ArrayDeque<>();

   HttpConnectionPoolImpl(HttpClientPoolImpl clientPool, EventLoop eventLoop, int size) {
      this.clientPool = clientPool;
      this.size = size;
      this.eventLoop = eventLoop;
   }

   @Override
   public HttpRequest request(HttpMethod method, String path, ByteBuf body) {
      HttpConnection connection = choose();
      return connection == null ? null : connection.request(method, path, body);
   }

   @Override
   public void registerWaitingSession(Session session) {
      waitingSessions.add(session);
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
   }

   @Override
   public Collection<HttpConnection> connections() {
      return connections;
   }

   private HttpConnection choose() {
      assert eventLoop.inEventLoop();
      // TODO prefer unused connections, O(1) execution...
      for (int i = 0; i < connections.size(); i++) {
         HttpConnection con = connections.get(i);
         if (con.isAvailable()) {
            log.trace("Using connection {}", con);
            return con;
         }
      }
      if (log.isTraceEnabled()) {
         for (HttpConnection con : connections) {
            log.trace("Unavailable connection {}", con);
         }
      }
      return null;
   }

   private synchronized void checkCreateConnections(int retry) {
      assert eventLoop.inEventLoop();

      if (shutdown) {
         return;
      }
      //TODO:: configurable
      if (retry > 100) {
         throw new IllegalStateException();
      }
      if (count < size) {
         count++;
         clientPool.connect(this, (conn, err) -> {
            // at this moment we're in unknown thread
            if (err != null) {
               log.warn("Cannot create connection", err);
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
         eventLoop.schedule(() -> {
            checkCreateConnections(retry);
         }, 2, TimeUnit.MILLISECONDS);

      }
   }

   private void connectionCreated(HttpConnection conn) {
      assert eventLoop.inEventLoop();

      Runnable handler = null;
      connections.add(conn);
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
         handler.run();
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

   void start(Runnable handler) {
      startedHandler = handler;
      eventLoop.execute(() -> checkCreateConnections(0));
   }

   void shutdown() {
      log.debug("Shutdown called");
      shutdown = true;
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

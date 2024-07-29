package io.hyperfoil.core.api;

import java.time.Clock;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.vertx.core.Future;

public interface PluginRunData {

   void initSession(Session session, int executorId, Scenario scenario, Clock clock);

   /**
    * Plugin should create any connections that it requires to properly perform the configured benchmark scenario.
    * This method should never block.If a call has operations that are non blocking it should register those as
    * <p>
    * Future objects to the provided <b>promiseCollector</b> so that the Hyperfoil framework can be notified when
    * the connections have all been established.
    * <p>
    * If the plugin cannot open a connection without blocking it can invoke the desired code in the provided
    * <b>blockingHandler</b> which will perform the operation on a blocking thread to prevent the method from blocking.
    * The future(s) returned from the handler should then be registered with the <b>promiseCollector</b>.
    *
    * @param blockingHandler Handler that can be used to run blocking code, returning a Future that must be registered
    * @param promiseCollector Collector to notify invoker that there outstanding operations that will complete at some
    *        point in the future.
    */
   void openConnections(Function<Callable<Void>, Future<Void>> blockingHandler, Consumer<Future<Void>> promiseCollector);

   void listConnections(Consumer<String> connectionCollector);

   void visitConnectionStats(ConnectionStatsConsumer consumer);

   void shutdown();
}

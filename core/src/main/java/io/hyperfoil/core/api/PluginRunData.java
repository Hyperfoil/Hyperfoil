package io.hyperfoil.core.api;

import java.time.Clock;
import java.util.function.Consumer;

import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.vertx.core.Future;

public interface PluginRunData {

   void initSession(Session session, int executorId, Scenario scenario, Clock clock);

   void openConnections(Consumer<Future<Void>> promiseCollector);

   void listConnections(Consumer<String> connectionCollector);

   void visitConnectionStats(ConnectionStatsConsumer consumer);

   void shutdown();
}

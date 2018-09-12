package io.sailrocket.core.api;

import java.util.Map;
import java.util.function.Consumer;

import io.sailrocket.api.Report;
import io.sailrocket.api.Session;

public interface SimulationRunner {
   void init() throws Exception;

   Map<String, Report> run() throws Exception;

   void shutdown();

   void visitSessions(Consumer<Session> consumer);
}

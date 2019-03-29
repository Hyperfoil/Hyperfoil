package io.hyperfoil.core.impl;

public interface SessionStatsConsumer {
   void accept(String phase, int minSessions, int maxSessions);
}

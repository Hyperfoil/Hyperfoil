package io.hyperfoil.api.connection;

public interface ConnectionStatsConsumer {
   void accept(String authority, String tag, int min, int max);
}

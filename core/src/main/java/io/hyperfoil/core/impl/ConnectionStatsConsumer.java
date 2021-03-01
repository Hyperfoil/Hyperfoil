package io.hyperfoil.core.impl;

public interface ConnectionStatsConsumer {
   void accept(String authority, String tag, int min, int max);
}

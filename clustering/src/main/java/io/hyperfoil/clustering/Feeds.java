package io.hyperfoil.clustering;

public final class Feeds {
   public static final String DISCOVERY = "discovery-feed";
   private static final String CONTROL_PREFIX = "control-feed.";
   public static final String RESPONSE = "response-feed";
   public static final String STATS = "stats-feed";

   private Feeds() {
   }

   /**
    * Phase control messages are addressed per run: a single address would be delivered to every agent registered on
    * the event bus, including the agents of other runs executing at the same time.
    */
   public static String control(String runId) {
      return CONTROL_PREFIX + runId;
   }
}

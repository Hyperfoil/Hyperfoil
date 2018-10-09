package io.sailrocket.clustering;

import io.sailrocket.api.config.Simulation;
import io.sailrocket.clustering.util.PhaseChangeMessage;
import io.sailrocket.clustering.util.PhaseControlMessage;
import io.sailrocket.clustering.util.ReportMessage;
import io.sailrocket.clustering.util.SimulationCodec;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

public final class Codecs {
   private Codecs() {}

   public static void register(Vertx vertx) {
      EventBus eb = vertx.eventBus();

      eb.registerDefaultCodec(Simulation.class, new SimulationCodec());
      eb.registerDefaultCodec(PhaseChangeMessage.class, new PhaseChangeMessage.Codec());
      eb.registerDefaultCodec(PhaseControlMessage.class, new PhaseControlMessage.Codec());
      eb.registerDefaultCodec(ReportMessage.class, new ReportMessage.Codec());
   }
}

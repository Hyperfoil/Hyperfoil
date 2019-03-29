package io.hyperfoil.clustering;

import java.util.ArrayList;

import io.hyperfoil.clustering.util.AgentControlMessage;
import io.hyperfoil.clustering.util.AgentHello;
import io.hyperfoil.clustering.util.ObjectCodec;
import io.hyperfoil.clustering.util.PhaseChangeMessage;
import io.hyperfoil.clustering.util.PhaseControlMessage;
import io.hyperfoil.clustering.util.ReportMessage;
import io.hyperfoil.clustering.util.SessionStatsMessage;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

public final class Codecs {
   private Codecs() {}

   public static void register(Vertx vertx) {
      EventBus eb = vertx.eventBus();

      eb.registerDefaultCodec(AgentHello.class, new AgentHello.Codec());
      eb.registerDefaultCodec(AgentControlMessage.class, new AgentControlMessage.Codec());
      eb.registerDefaultCodec(PhaseChangeMessage.class, new PhaseChangeMessage.Codec());
      eb.registerDefaultCodec(PhaseControlMessage.class, new PhaseControlMessage.Codec());
      eb.registerDefaultCodec(ReportMessage.class, new ReportMessage.Codec());
      eb.registerDefaultCodec(ArrayList.class, new ObjectCodec.ArrayList());
      eb.registerDefaultCodec(SessionStatsMessage.class, new SessionStatsMessage.Codec());
   }
}

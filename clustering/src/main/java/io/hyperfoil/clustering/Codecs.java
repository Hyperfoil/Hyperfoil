package io.hyperfoil.clustering;

import java.util.ArrayList;

import io.hyperfoil.clustering.messages.AgentControlMessage;
import io.hyperfoil.clustering.messages.AgentHello;
import io.hyperfoil.clustering.messages.ObjectCodec;
import io.hyperfoil.clustering.messages.PhaseChangeMessage;
import io.hyperfoil.clustering.messages.PhaseControlMessage;
import io.hyperfoil.clustering.messages.ReportMessage;
import io.hyperfoil.clustering.messages.SessionStatsMessage;
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

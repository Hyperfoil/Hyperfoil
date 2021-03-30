package io.hyperfoil.clustering;

import java.util.ArrayList;

import io.hyperfoil.clustering.messages.AgentControlMessage;
import io.hyperfoil.clustering.messages.AgentHello;
import io.hyperfoil.clustering.messages.AgentReadyMessage;
import io.hyperfoil.clustering.messages.AuxiliaryHello;
import io.hyperfoil.clustering.messages.ConnectionStatsMessage;
import io.hyperfoil.clustering.messages.ErrorMessage;
import io.hyperfoil.clustering.messages.ObjectCodec;
import io.hyperfoil.clustering.messages.PhaseChangeMessage;
import io.hyperfoil.clustering.messages.PhaseControlMessage;
import io.hyperfoil.clustering.messages.RequestStatsMessage;
import io.hyperfoil.clustering.messages.SessionStatsMessage;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

public final class Codecs {
   private Codecs() {}

   public static void register(Vertx vertx) {
      EventBus eb = vertx.eventBus();

      eb.registerDefaultCodec(AgentHello.class, new AgentHello.Codec());
      eb.registerDefaultCodec(AgentControlMessage.class, new AgentControlMessage.Codec());
      eb.registerDefaultCodec(AgentReadyMessage.class, new AgentReadyMessage.Codec());
      eb.registerDefaultCodec(ArrayList.class, new ObjectCodec.ArrayList());
      eb.registerDefaultCodec(AuxiliaryHello.class, new AuxiliaryHello.Codec());
      eb.registerDefaultCodec(ConnectionStatsMessage.class, new ConnectionStatsMessage.Codec());
      eb.registerDefaultCodec(ErrorMessage.class, new ErrorMessage.Codec());
      eb.registerDefaultCodec(PhaseChangeMessage.class, new PhaseChangeMessage.Codec());
      eb.registerDefaultCodec(PhaseControlMessage.class, new PhaseControlMessage.Codec());
      eb.registerDefaultCodec(RequestStatsMessage.class, new RequestStatsMessage.Codec());
      eb.registerDefaultCodec(SessionStatsMessage.class, new SessionStatsMessage.Codec());
   }
}

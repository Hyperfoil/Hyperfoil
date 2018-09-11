package io.sailrocket.core.session;

import java.util.Collections;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Phase;
import io.sailrocket.api.Sequence;
import io.sailrocket.api.Session;
import io.sailrocket.core.impl.ScenarioImpl;

public final class SessionFactory {
   public static Session create(HttpClientPool httpClientPool, Phase phase, int uniqueId) {
      return new SessionImpl(httpClientPool, phase, uniqueId);
   }

   public static Session forTesting() {
      ScenarioImpl dummyScenario = new ScenarioImpl(new Sequence[0], new Sequence[0], new String[0], new String[0]);
      Phase dummyPhase = new Phase("dummy", dummyScenario, 0, Collections.emptyList(), Collections.emptyList(), 0, -1) {
         @Override
         protected void proceed(HttpClientPool clientPool) {
         }

         @Override
         public void reserveSessions() {
         }
      };
      return new SessionImpl(null, dummyPhase, 0);
   }

   private SessionFactory() {
   }
}

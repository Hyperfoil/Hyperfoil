package io.sailrocket.core.session;

import java.util.Collections;

import io.netty.util.concurrent.EventExecutorGroup;
import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.Scenario;
import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.impl.PhaseInstanceImpl;

public final class SessionFactory {
   public static Session create(HttpClientPool httpClientPool, Scenario scenario, int uniqueId) {
      return new SessionImpl(httpClientPool, scenario, uniqueId);
   }

   public static void resetPhase(Session session, PhaseInstance phase) {
      ((SessionImpl) session).resetPhase(phase);
   }

   public static Session forTesting() {
      Scenario dummyScenario = new Scenario(new Sequence[0], new Sequence[0], new String[0], new String[0]);
      SessionImpl session = new SessionImpl(null, dummyScenario, 0);
      session.resetPhase(new PhaseInstanceImpl(new Phase("dummy", dummyScenario, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, -1, null) {}) {
         @Override
         public void proceed(EventExecutorGroup executorGroup) {
         }

         @Override
         public void reserveSessions() {
         }
      });
      return session;
   }

   private SessionFactory() {
   }
}

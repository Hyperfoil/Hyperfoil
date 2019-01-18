package io.hyperfoil.core.session;

import java.util.Collections;

import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.impl.PhaseInstanceImpl;

public final class SessionFactory {
   public static Session create(Scenario scenario, int uniqueId) {
      return new SessionImpl(scenario, uniqueId);
   }

   public static Session forTesting() {
      Scenario dummyScenario = new Scenario(new Sequence[0], new Sequence[0], new String[0], new String[0]);
      SessionImpl session = new SessionImpl(dummyScenario, 0);
      Phase dummyPhase = new Phase(() -> null, "dummy", dummyScenario, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, -1, 0, null) {
      };
      session.resetPhase(new PhaseInstanceImpl<Phase>(dummyPhase) {
         @Override
         public void proceed(EventExecutorGroup executorGroup) {
         }

         @Override
         public void reserveSessions() {
         }
      });
      session.attach(ImmediateEventExecutor.INSTANCE, null, new Statistics[]{new Statistics()});
      return session;
   }

   private SessionFactory() {
   }
}

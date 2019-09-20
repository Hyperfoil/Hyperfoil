package io.hyperfoil.core.session;

import java.time.Clock;
import java.util.Collections;

import io.hyperfoil.api.session.Access;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.impl.PhaseInstanceImpl;

public final class SessionFactory {
   private static final Clock DEFAULT_CLOCK = Clock.systemDefaultZone();

   public static Session create(Scenario scenario, int agentId, int executorId, int uniqueId) {
      return new SessionImpl(scenario, agentId, executorId, uniqueId, DEFAULT_CLOCK);
   }

   public static Session forTesting() {
      return forTesting(Clock.systemDefaultZone());
   }

   public static Session forTesting(Clock clock) {
      Scenario dummyScenario = new Scenario(new Sequence[0], new Sequence[0], new String[0], new String[0]);
      SessionImpl session = new SessionImpl(dummyScenario, 0, 0, 0, clock);
      Phase dummyPhase = new Phase(() -> null, 0, "dummy", dummyScenario, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, -1, 0, null) {
         @Override
         public String description() {
            return "dummy";
         }
      };
      session.resetPhase(new PhaseInstanceImpl<Phase>(dummyPhase) {
         @Override
         public void proceed(EventExecutorGroup executorGroup) {
         }

         @Override
         public void reserveSessions() {
         }
      });
      session.attach(ImmediateEventExecutor.INSTANCE, null, null, null);
      session.reserve(dummyScenario);
      return session;
   }

   private SessionFactory() {
   }

   public static Access access(Object key) {
      if (key == null) {
         return null;
      } else if (key instanceof String) {
         String expression = (String) key;
         if (expression.endsWith("[.]")) {
            return new SequenceScopedAccess(expression.substring(0, expression.length() - 3));
         } else {
            return new SimpleAccess(key);
         }
      } else {
         return new SimpleAccess(key);
      }
   }
}

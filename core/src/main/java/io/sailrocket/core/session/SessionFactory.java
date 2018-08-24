package io.sailrocket.core.session;

import java.util.function.BooleanSupplier;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Scenario;
import io.sailrocket.api.Sequence;
import io.sailrocket.api.Session;
import io.sailrocket.core.impl.ScenarioImpl;

public final class SessionFactory {
   public static Session create(HttpClientPool httpClientPool, int maxConcurrency, int maxEnabledSequences, BooleanSupplier termination, Scenario scenario) {
      return new SessionImpl(httpClientPool, maxConcurrency, maxEnabledSequences, termination, scenario);
   }

   public static Session forTesting() {
      ScenarioImpl dummyScenario = new ScenarioImpl(new Sequence[0], new Sequence[0], new String[0], new String[0]);
      return new SessionImpl(null, 1, 1, () -> false, dummyScenario);
   }

   private SessionFactory() {
   }
}

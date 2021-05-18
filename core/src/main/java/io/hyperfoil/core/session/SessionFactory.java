package io.hyperfoil.core.session;

import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.WriteAccess;
import io.hyperfoil.core.impl.PhaseInstanceImpl;
import io.hyperfoil.core.util.Unique;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;

public final class SessionFactory {
   private static final SpecialAccess[] SPECIAL = {
         new SpecialAccess.Int("hyperfoil.agent.id", Session::agentId),
         new SpecialAccess.Int("hyperfoil.agents", Session::agents),
         new SpecialAccess.Int("hyperfoil.agent.thread.id", Session::agentThreadId),
         new SpecialAccess.Int("hyperfoil.agent.threads", Session::agentThreads),
         new SpecialAccess.Int("hyperfoil.global.thread.id", Session::globalThreadId),
         new SpecialAccess.Int("hyperfoil.global.threads", Session::globalThreads),
         new SpecialAccess.Object("hyperfoil.phase.name", s -> s.phase().definition().name),
         new SpecialAccess.Int("hyperfoil.phase.id", s -> s.phase().definition().id),
         new SpecialAccess.Int("hyperfoil.phase.iteration", s -> s.phase().definition().iteration),
         new SpecialAccess.Object("hyperfoil.run.id", Session::runId),
         new SpecialAccess.Int("hyperfoil.session.id", Session::uniqueId),
         };

   public static Session create(Scenario scenario, int executorId, int uniqueId) {
      return new SessionImpl(scenario, executorId, uniqueId);
   }

   public static Session forTesting(WriteAccess... accesses) {
      Scenario dummyScenario = new Scenario(new Sequence[0], new Sequence[] {
            new Sequence("dummy", 0, 1, 0, new Step[0]) {
               WriteAccess[] dummyAccesses = accesses;
            }
      }, 16, 16);
      SessionImpl session = new SessionImpl(dummyScenario, 0, 0);
      Phase dummyPhase = new Phase(Benchmark::forTesting, 0, 0, "dummy", dummyScenario, 0,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, -1, null, false, () -> "dummy");
      session.resetPhase(new PhaseInstanceImpl(dummyPhase, "dummy", 0) {
         @Override
         public void proceed(EventExecutorGroup executorGroup) {
         }

         @Override
         public void reserveSessions() {
         }
      });
      session.attach(ImmediateEventExecutor.INSTANCE, null, null);
      session.reserve(dummyScenario);
      return session;
   }

   private SessionFactory() {
   }

   public static ReadAccess readAccess(Object key) {
      if (key instanceof String) {
         String expression = (String) key;
         if (expression.startsWith("hyperfoil.")) {
            for (SpecialAccess access : SPECIAL) {
               if (access.name.equals(expression)) {
                  return access;
               }
            }
            throw new BenchmarkDefinitionException("No special variable " + expression);
         }
      }
      return access(key, SimpleReadAccess::new, SequenceScopedReadAccess::new);
   }

   public static ObjectAccess objectAccess(Object key) {
      return access(key, SimpleObjectAccess::new, SequenceScopedObjectAccess::new);
   }

   public static IntAccess intAccess(Object key) {
      return access(key, SimpleIntAccess::new, SequenceScopedIntAccess::new);
   }

   public static <A extends ReadAccess> A access(Object key, Function<Object, A> simple, BiFunction<Object, Integer, A> sequenceScoped) {
      // This should be invoked only from prepareBuild() or build()
      assert Locator.current() != null;
      if (key == null) {
         return null;
      } else if (key instanceof String) {
         String expression = (String) key;
         if (expression.endsWith("[.]")) {
            return sequenceScoped.apply(expression.substring(0, expression.length() - 3), getMaxConcurrency());
         } else {
            return simple.apply(key);
         }
      } else if (key instanceof Unique) {
         if (((Unique) key).isSequenceScoped()) {
            return sequenceScoped.apply(key, getMaxConcurrency());
         } else {
            return simple.apply(key);
         }
      } else {
         return simple.apply(key);
      }
   }

   public static ReadAccess sequenceScopedReadAccess(Object key) {
      return sequenceScopedObjectAccess(key);
   }

   public static ObjectAccess sequenceScopedObjectAccess(Object key) {
      return new SequenceScopedObjectAccess(key, getMaxConcurrency());
   }

   public static IntAccess sequenceScopedIntAccess(Object key) {
      return new SequenceScopedIntAccess(key, getMaxConcurrency());
   }

   private static int getMaxConcurrency() {
      Locator locator = Locator.current();
      assert locator != null;
      int maxConcurrency = locator.sequence().rootSequence().concurrency();
      if (maxConcurrency <= 0) {
         throw new BenchmarkDefinitionException(locator.step() + " in sequence " + locator.sequence().name() +
               " uses sequence-scoped access but this sequence is not declared as concurrent.");
      }
      return maxConcurrency;
   }
}

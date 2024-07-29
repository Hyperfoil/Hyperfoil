package io.hyperfoil.core.impl;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.session.Session;
import io.netty.util.concurrent.EventExecutor;

@State(Scope.Benchmark)
@Fork(value = 2)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PoolReserveBenchmark {

   @Param({ "128", "1024", "131072" })
   private int capacity;

   @Param({ "0", "1", "2", "4" })
   private int eventExecutors;

   private ElasticPool<Session> pool;

   @Setup
   public void setup() {
      var eventExecutors = new EventExecutor[this.eventExecutors];
      for (int i = 0; i < eventExecutors.length; i++) {
         eventExecutors[i] = new FakeEventExecutor();
      }
      var sessions = new ArrayDeque<Session>(capacity);
      long executorSequence = 0;
      for (int i = 0; i < capacity; i++) {
         Session session;
         if (eventExecutors.length > 0) {
            int agentThreadId = (int) (executorSequence++ % eventExecutors.length);
            session = new FakeSession(eventExecutors[agentThreadId], agentThreadId);
         } else {
            session = new FakeSession(null, 0);
         }
         sessions.add(session);
      }
      // this is just to have some class loading done, really
      // but need to be careful to not trigger too much JIT!
      var sessionsCopy = new ArrayDeque<>(sessions);
      if (eventExecutors.length > 0) {
         pool = new AffinityAwareSessionPool(eventExecutors, sessionsCopy::poll);
      } else {
         pool = new LockBasedElasticPool<>(sessionsCopy::poll, () -> {
            System.exit(1);
            return null;
         });
      }
      if (eventExecutors.length > 0) {
         pool.reserve(eventExecutors.length);
         pool = new AffinityAwareSessionPool(eventExecutors, sessions::poll);
      } else {
         pool.reserve(1);
         pool = new LockBasedElasticPool<>(sessions::poll, () -> {
            System.exit(1);
            return null;
         });
      }
   }

   @Benchmark
   public void reserve() {
      pool.reserve(capacity);
   }

}

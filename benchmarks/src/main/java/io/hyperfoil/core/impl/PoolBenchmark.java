package io.hyperfoil.core.impl;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.harness.EventLoopGroupHarnessExecutor;
import io.netty.util.concurrent.EventExecutor;

@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = { "-Djmh.executor=CUSTOM",
      "-Djmh.executor.class=io.hyperfoil.core.harness.EventLoopGroupHarnessExecutor" })
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PoolBenchmark {

   @Param({ "0" })
   private int work;
   @Param({ "1024" })
   private int capacity;
   @Param({ "1" })
   private int burst;
   @Param({ "true" })
   private boolean workStealingPool;
   @Param({ "0", "16" })
   private int fakeEventExecutors;

   private ElasticPool<Session> pool;

   @Setup
   public void setup(BenchmarkParams params) {
      if (EventLoopGroupHarnessExecutor.TOTAL_EVENT_EXECUTORS.size() != 1) {
         throw new IllegalStateException("Expected exactly one EventLoopExecutor");
      }
      var executorGroup = EventLoopGroupHarnessExecutor.TOTAL_EVENT_EXECUTORS.iterator().next();
      var executors = StreamSupport.stream(executorGroup.spliterator(), false)
            .map(EventExecutor.class::cast).toArray(EventExecutor[]::new);
      if (executors.length != params.getThreads()) {
         throw new IllegalStateException("Expected " + params.getThreads() + " executors, got " + executors.length);
      }
      executors = Arrays.copyOf(executors, executors.length + fakeEventExecutors);
      // create some fake EventExecutors here, if needed
      for (int i = executors.length - fakeEventExecutors; i < executors.length; i++) {
         executors[i] = new FakeEventExecutor();
      }
      // sessions are distributed among the perceived event executors, in round-robin
      var sessions = createSessions(executors, capacity);
      if (!workStealingPool) {
         pool = new LockBasedElasticPool<>(sessions::poll, () -> {
            System.exit(1);
            return null;
         });
      } else {
         pool = new AffinityAwareSessionPool(executors, sessions::poll);
      }
      pool.reserve(capacity);
   }

   protected static Queue<Session> createSessions(EventExecutor[] executors, int sessions) {
      var queue = new ArrayDeque<Session>(sessions);
      for (int i = 0; i < sessions; i++) {
         int agentThreadId = i % executors.length;
         final var eventExecutor = executors[agentThreadId];
         final Session session = new FakeSession(eventExecutor, agentThreadId);
         queue.add(session);
      }
      return queue;
   }

   @AuxCounters
   @State(Scope.Thread)
   public static class Counters {
      public long acquired;
      public long released;
      private ArrayDeque<Session> pooledAcquired;

      @Setup
      public void setup(PoolBenchmark benchmark) {
         pooledAcquired = new ArrayDeque<>(benchmark.burst);
      }
   }

   @Benchmark
   public int acquireAndRelease(Counters counters) {
      var pool = this.pool;
      int acquired = 0;
      var pooledAcquired = counters.pooledAcquired;
      for (int i = 0; i < burst; ++i) {
         var pooledObject = pool.acquire();
         pooledAcquired.add(pooledObject);
      }
      counters.acquired += burst;
      int work = this.work;
      if (work > 0) {
         Blackhole.consumeCPU(work);
      }
      for (int i = 0; i < burst; ++i) {
         pool.release(pooledAcquired.poll());
      }
      counters.released += burst;
      return acquired;
   }

}

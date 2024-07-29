package io.hyperfoil.core.counters;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;

@State(Scope.Benchmark)
@Fork(value = 2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CountersScalability {

   @State(Scope.Thread)
   public static class ThreadLocalCounter {

      private static final AtomicIntegerFieldUpdater<ThreadLocalCounter> COUNTER_UPDATER = AtomicIntegerFieldUpdater
            .newUpdater(ThreadLocalCounter.class, "counter");
      private volatile int counter;
      private int counterId;

      @Setup
      public void setup(CountersScalability countersScalability) {
         counterId = countersScalability.registerThreadState(this);
      }

      public int weakIncrementAndGet() {
         int nextValue = counter + 1;
         COUNTER_UPDATER.lazySet(this, nextValue);
         return nextValue;
      }

      public int atomicIncrementAndGet() {
         return COUNTER_UPDATER.incrementAndGet(this);
      }

      public int getCounter() {
         return counter;
      }

      public int atomicDecrementAndGet() {
         return COUNTER_UPDATER.decrementAndGet(this);
      }

      public int weakDecrementAndGet() {
         int nextValue = counter - 1;
         COUNTER_UPDATER.lazySet(this, nextValue);
         return nextValue;
      }
   }

   private static final AtomicIntegerFieldUpdater<CountersScalability> MIN_UPDATER = AtomicIntegerFieldUpdater
         .newUpdater(CountersScalability.class, "min");
   private static final AtomicIntegerFieldUpdater<CountersScalability> MAX_UPDATER = AtomicIntegerFieldUpdater
         .newUpdater(CountersScalability.class, "max");
   private static final AtomicIntegerFieldUpdater<CountersScalability> SHARED_COUNTER_UPDATER = AtomicIntegerFieldUpdater
         .newUpdater(CountersScalability.class, "sharedCounter");

   private AtomicInteger nextCounter;
   private ThreadLocalCounter[] counters;
   private int threads;
   private volatile int min;
   private volatile int max;
   private volatile int sharedCounter;

   @Setup
   public void setup(BenchmarkParams params) {
      threads = params.getThreads();
      nextCounter = new AtomicInteger();
      counters = new ThreadLocalCounter[threads];
   }

   private int registerThreadState(ThreadLocalCounter threadLocalCounter) {
      int counterId = nextCounter.getAndIncrement();
      counters[counterId] = threadLocalCounter;
      return counterId;
   }

   private int atomicIncrement(ThreadLocalCounter counter) {
      var counters = this.counters;
      int indexToSkip = counter.counterId;
      int threads = this.threads;
      int usage = counter.atomicIncrementAndGet();
      for (int i = 0; i < threads; i++) {
         if (i != indexToSkip) {
            usage += counters[i].getCounter();
         }
      }
      if (usage > max) {
         MAX_UPDATER.lazySet(this, usage);
      }
      return usage;
   }

   private int atomicDecrement(ThreadLocalCounter counter) {
      var counters = this.counters;
      int indexToSkip = counter.counterId;
      int threads = this.threads;
      int usage = counter.atomicDecrementAndGet();
      for (int i = 0; i < threads; i++) {
         if (i != indexToSkip) {
            usage += counters[i].getCounter();
         }
      }
      if (usage < min) {
         MIN_UPDATER.lazySet(this, usage);
      }
      return usage;
   }

   private int weakIncrement(ThreadLocalCounter counter) {
      var counters = this.counters;
      int indexToSkip = counter.counterId;
      int threads = this.threads;
      int usage = counter.weakIncrementAndGet();
      for (int i = 0; i < threads; i++) {
         if (i != indexToSkip) {
            usage += counters[i].getCounter();
         }
      }
      if (usage > max) {
         MAX_UPDATER.lazySet(this, usage);
      }
      return usage;
   }

   private int weakDecrement(ThreadLocalCounter counter) {
      var counters = this.counters;
      int indexToSkip = counter.counterId;
      int threads = this.threads;
      int usage = counter.weakDecrementAndGet();
      for (int i = 0; i < threads; i++) {
         if (i != indexToSkip) {
            usage += counters[i].getCounter();
         }
      }
      if (usage < min) {
         MIN_UPDATER.lazySet(this, usage);
      }
      return usage;
   }

   @Benchmark
   public int atomicUsage(ThreadLocalCounter counter) {
      int up = atomicIncrement(counter);
      int down = atomicDecrement(counter);
      return up + down;
   }

   @Benchmark
   public int weakUsage(ThreadLocalCounter counter) {
      int up = weakIncrement(counter);
      int down = weakDecrement(counter);
      return up + down;
   }

   @Benchmark
   public int sharedUsage() {
      int up = sharedIncrement();
      int down = sharedDecrement();
      return up + down;
   }

   private int sharedDecrement() {
      int value = SHARED_COUNTER_UPDATER.decrementAndGet(this);
      int min = this.min;
      if (min > 0 && value < min) {
         MIN_UPDATER.lazySet(this, value);
      }
      return value;
   }

   private int sharedIncrement() {
      int value = SHARED_COUNTER_UPDATER.incrementAndGet(this);
      if (value > max) {
         MAX_UPDATER.lazySet(this, value);
      }
      return value;
   }

}

package io.hyperfoil.core.impl;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Supplier;

import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.session.Session;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadExecutorMap;

/**
 * This class represents a pool of sessions that are aware of thread affinity.
 * It implements the {@link ElasticPool} interface and provides methods to acquire and release sessions.<br>
 * The pool maintains a separate queue of sessions for each event executor (thread),
 * and tries to acquire and release sessions from the queue of the current executor.
 * If the local queue is empty, it tries to steal sessions from other queues.
 */
public class AffinityAwareSessionPool implements ElasticPool<Session> {

   private final FastThreadLocal<Integer> localAgentThreadId;
   private final IdentityHashMap<EventExecutor, Integer> agentThreadIdPerExecutor;
   private final Queue<Session>[] localQueues;
   private final Supplier<Session> sessionSupplier;

   /**
    * We're using a single array to store multiple counters, mostly to avoid false sharing with other fields
    * in this same class (which are mostly read-only).<br>
    * We still pad used/min/max from each others to avoid max and min to interfere with each other.
    * There's no point to use a LongAdder here, because reading the counters would hit worse than sharing a single one
    * which can leverage fast x86 fetch-and-add instructions.
    */
   private static final int PADDING_INTS = 128 / Integer.BYTES; // 32
   private static final int USED_OFFSET = PADDING_INTS; // 32
   private static final int MIN_USED_OFFSET = USED_OFFSET + PADDING_INTS; // 32 + 32 = 64
   private static final int MAX_USED_OFFSET = MIN_USED_OFFSET + PADDING_INTS; // 64 + 32 = 96
   private static final int COUNTERS_INTS = MAX_USED_OFFSET + PADDING_INTS; // 96 + 32 = 128
   private final AtomicIntegerArray counters;

   public AffinityAwareSessionPool(EventExecutor[] eventExecutors, Supplier<Session> sessionSupplier) {
      this.sessionSupplier = sessionSupplier;
      this.agentThreadIdPerExecutor = new IdentityHashMap<>(eventExecutors.length);
      this.localQueues = new Queue[eventExecutors.length];
      for (int agentThreadId = 0; agentThreadId < eventExecutors.length; agentThreadId++) {
         agentThreadIdPerExecutor.put(eventExecutors[agentThreadId], agentThreadId);
      }
      this.localAgentThreadId = new FastThreadLocal<>() {
         @Override
         protected Integer initialValue() {
            var eventExecutor = ThreadExecutorMap.currentExecutor();
            return eventExecutor == null ? null : agentThreadIdPerExecutor.get(eventExecutor);
         }
      };
      this.counters = new AtomicIntegerArray(COUNTERS_INTS);
   }

   private int nextWorkStealIndex() {
      return ThreadLocalRandom.current().nextInt(0, localQueues.length);
   }

   private int nextWorkStealIndex(int excludeIndex) {
      var localQueuesCount = localQueues.length;
      var workStealIndex = localQueuesCount == 2 ? excludeIndex : ThreadLocalRandom.current().nextInt(0, localQueuesCount);
      if (workStealIndex == excludeIndex) {
         workStealIndex++;
         if (workStealIndex == localQueuesCount) {
            workStealIndex = 0;
         }
      }
      return workStealIndex;
   }

   @Override
   public Session acquire() {
      var boxedAgentThreadId = localAgentThreadId.get();
      if (boxedAgentThreadId == null) {
         return acquireFromLocalQueues();
      }
      // explicitly unbox to save unboxing it again, in case work-stealing happens
      var agentThreadId = boxedAgentThreadId.intValue();
      var localQueues = this.localQueues;
      var session = localQueues[agentThreadId].poll();
      if (session != null) {
         incrementUsed();
         return session;
      }
      if (localQueues.length == 1) {
         return null;
      }
      return acquireFromOtherLocalQueues(localQueues, agentThreadId);
   }

   private Session acquireFromLocalQueues() {
      int currentIndex = nextWorkStealIndex();
      var localQueues = this.localQueues;
      int localQueuesCount = localQueues.length;
      for (Queue<Session> localQueue : localQueues) {
         Session session = localQueue.poll();
         if (session != null) {
            incrementUsed();
            return session;
         }
         currentIndex++;
         if (currentIndex == localQueuesCount) {
            currentIndex = 0;
         }
      }
      return null;
   }

   private Session acquireFromOtherLocalQueues(Queue<Session>[] localQueues, int agentThreadIdToSkip) {
      int currentIndex = nextWorkStealIndex(agentThreadIdToSkip);
      var localQueuesCount = localQueues.length;
      // this loop start from the workStealIndex, but while iterating can still end up
      // at the index we want to ignore, so we need to skip it
      for (int i = 0; i < localQueuesCount; i++) {
         if (currentIndex != agentThreadIdToSkip) {
            var localQ = localQueues[currentIndex];
            if (localQ != null) {
               var session = localQ.poll();
               if (session != null) {
                  incrementUsed();
                  return session;
               }
            }
         }
         currentIndex++;
         if (currentIndex == localQueuesCount) {
            currentIndex = 0;
         }
      }
      return null;
   }

   private void incrementUsed() {
      var counters = this.counters;
      int used = counters.incrementAndGet(USED_OFFSET);
      int maxUsed = counters.get(MAX_USED_OFFSET);
      if (used > maxUsed) {
         counters.lazySet(MAX_USED_OFFSET, used);
      }
   }

   private void decrementUsed() {
      var counters = this.counters;
      int used = counters.decrementAndGet(USED_OFFSET);
      int minUsed = counters.get(MIN_USED_OFFSET);
      if (minUsed > 0 && used < minUsed) {
         counters.lazySet(MIN_USED_OFFSET, used);
      }
   }

   @Override
   public void release(Session session) {
      Objects.requireNonNull(session);
      var localQueue = localQueues[session.agentThreadId()];
      decrementUsed();
      localQueue.add(session);
   }

   @Override
   public void reserve(int capacity) {
      int totalCapacity = getLocalQueuesCapacity();
      if (totalCapacity >= capacity) {
         return;
      }
      moveNewSessionsToLocalQueues(capacity, totalCapacity);
   }

   private int getLocalQueuesCapacity() {
      int totalCapacity = 0;
      for (Queue<Session> localQueue : localQueues) {
         if (localQueue != null) {
            totalCapacity += localQueue.size();
         }
      }
      return totalCapacity;
   }

   private void moveNewSessionsToLocalQueues(int requiredCapacity, int currentCapacity) {
      final int newCapacity = requiredCapacity - currentCapacity;
      // assume fair distribution of new sessions
      var perEventExecutorNewCapacity = (int) Math.ceil((double) newCapacity / localQueues.length);
      var sessionSupplier = this.sessionSupplier;
      var localQueues = this.localQueues;
      var agentThreadIdPerExecutor = this.agentThreadIdPerExecutor;
      // It keeps track of the local queues capacity not yet reserved
      boolean[] localQueueReservedCapacity = new boolean[localQueues.length];
      for (int i = 0; i < newCapacity; i++) {
         var newSession = sessionSupplier.get();
         var eventExecutor = newSession.executor();
         var boxedAgentThreadId = agentThreadIdPerExecutor.get(eventExecutor);
         if (boxedAgentThreadId == null) {
            throw new IllegalStateException("No agentThreadId for executor " + eventExecutor);
         }
         var agentThreadId = boxedAgentThreadId.intValue();
         var localQueue = localQueues[agentThreadId];
         if (!localQueueReservedCapacity[agentThreadId]) {
            localQueueReservedCapacity[agentThreadId] = true;
            if (localQueue == null) {
               localQueue = createLocalQueue(perEventExecutorNewCapacity);
               localQueues[agentThreadId] = localQueue;
            } else {
               var newLocalQueue = createLocalQueue(localQueue.size() + perEventExecutorNewCapacity);
               newLocalQueue.addAll(localQueue);
               localQueue.clear();
               localQueue = newLocalQueue;
               localQueues[agentThreadId] = newLocalQueue;
            }
         }
         if (!localQueue.offer(newSession)) {
            throw new IllegalStateException("Failed to add new session to local queue: sessions are not fairly distributed");
         }
      }
      for (int i = 0; i < localQueueReservedCapacity.length; i++) {
         if (!localQueueReservedCapacity[i]) {
            var localQ = localQueues[i];
            if (localQ == null) {
               localQ = createLocalQueue(0);
               this.localQueues[i] = localQ;
            }
         }
      }
   }

   private Queue<Session> createLocalQueue(int capacity) {
      if (PlatformDependent.hasUnsafe()) {
         return new MpmcArrayQueue<>(Math.max(2, capacity));
      }
      return new MpmcAtomicArrayQueue<>(Math.max(2, capacity));
   }

   @Override
   public int minUsed() {
      return counters.get(MIN_USED_OFFSET);
   }

   @Override
   public int maxUsed() {
      return counters.get(MAX_USED_OFFSET);
   }

   @Override
   public void resetStats() {
      var counters = this.counters;
      int used = counters.get(USED_OFFSET);
      counters.lazySet(MAX_USED_OFFSET, used);
      counters.lazySet(MIN_USED_OFFSET, used);
   }
}

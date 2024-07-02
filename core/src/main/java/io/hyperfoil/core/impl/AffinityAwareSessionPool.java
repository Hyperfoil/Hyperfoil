package io.hyperfoil.core.impl;

import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.session.Session;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadExecutorMap;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Supplier;

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
    private static final int INTS_PER_CACHE_LINE = 64 / Integer.BYTES;
    private static final int USED_OFFSET = INTS_PER_CACHE_LINE;
    private static final int MIN_USED_OFFSET = INTS_PER_CACHE_LINE * 2;
    private static final int MAX_USED_OFFSET = INTS_PER_CACHE_LINE * 3;
    private static final int COUNTERS_INTS = INTS_PER_CACHE_LINE * 4;
    private final AtomicIntegerArray counters;


    public AffinityAwareSessionPool(EventExecutor[] eventExecutors, Supplier<Session> sessionSupplier) {
        this.sessionSupplier = sessionSupplier;
        this.agentThreadIdPerExecutor = new IdentityHashMap<>(eventExecutors.length);
        this.localQueues = new Queue[eventExecutors.length];
        for (int agentThreadId = 0; agentThreadId < eventExecutors.length; agentThreadId++) {
            var boxedAgentThreadId = agentThreadId;
            agentThreadIdPerExecutor.put(eventExecutors[agentThreadId], boxedAgentThreadId);
        }
        this.localAgentThreadId = new FastThreadLocal<>() {
            @Override
            protected Integer initialValue() {
                var eventExecutor = ThreadExecutorMap.currentExecutor();
                if (eventExecutor == null) {
                    return null;
                }
                var agentThreadId = agentThreadIdPerExecutor.get(eventExecutor);
                if (agentThreadId == null) {
                    return null;
                }
                return agentThreadId;
            }
        };
        this.counters = new AtomicIntegerArray(COUNTERS_INTS);
    }

    private int nextWorkStealIndex() {
        return ThreadLocalRandom.current().nextInt(0, localQueues.length);
    }

    private int nextWorkStealIndex(int excludeIndex) {
        var localQueuesCount = localQueues.length;
        var workStealIndex = ThreadLocalRandom.current().nextInt(0, localQueuesCount);
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
        int workStealIndex = nextWorkStealIndex();
        int currentIndex = workStealIndex;
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
        int workStealIndex = nextWorkStealIndex(agentThreadIdToSkip);
        int currentIndex = workStealIndex;
        var localQueuesCount = localQueues.length;
        // this loop start from the workStealIndex, but while iterating can still end up
        // at the index we want to ignore, so we need to skip it
        for (int i = 0; i < localQueuesCount; i++) {
            if (currentIndex != agentThreadIdToSkip) {
                var session = localQueues[currentIndex].poll();
                if (session != null) {
                    incrementUsed();
                    return session;
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
        moveNewSessionsToLocalQueues(createSessionShards(capacity, totalCapacity));
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

    private void moveNewSessionsToLocalQueues(Queue<Session>[] configLocalQueues) {
        for (int i = 0; i < configLocalQueues.length; i++) {
            var newSessions = configLocalQueues[i];
            var runtimeLocalQueue = localQueues[i];
            if (runtimeLocalQueue == null) {
                runtimeLocalQueue = createLocalQueue(newSessions.size());
            } else {
                var newLocalQueue = createLocalQueue(newSessions.size() + runtimeLocalQueue.size());
                newLocalQueue.addAll(runtimeLocalQueue);
                runtimeLocalQueue.clear();
                runtimeLocalQueue = newLocalQueue;
            }
            runtimeLocalQueue.addAll(newSessions);
            newSessions.clear();
            localQueues[i] = runtimeLocalQueue;
        }
    }

    private Queue<Session>[] createSessionShards(int requiredCapacity, int availableCapacity) {
        var newSessionsPerEventExecutors = new Queue[localQueues.length];
        var additionalCapacity = requiredCapacity - availableCapacity;
        var perEventExecutorCapacity = (int) Math.ceil((double) additionalCapacity / localQueues.length);
        for (int i = 0; i < additionalCapacity; i++) {
            var newSession = sessionSupplier.get();
            if (newSession == null) {
                throw new IllegalStateException("Session supplier returned null");
            }
            var executor = newSession.executor();
            var boxedAgentThreadId = agentThreadIdPerExecutor.get(executor);
            if (boxedAgentThreadId == null) {
                throw new IllegalStateException("This session is associated with an unknown EventExecutor");
            }
            // sanity check against the sessions agentThreadId
            if (boxedAgentThreadId.intValue() != newSession.agentThreadId()) {
                throw new IllegalStateException("Session::getAgentThreadId is not associated with the provided EventExecutor");
            }
            var agentThreadId = boxedAgentThreadId.intValue();
            if (newSessionsPerEventExecutors[agentThreadId] == null) {
                newSessionsPerEventExecutors[agentThreadId] = new ArrayDeque<>(perEventExecutorCapacity);
            }
            newSessionsPerEventExecutors[agentThreadId].add(newSession);
        }
        // if there are more configured executors than the number of sessions, we need to create empty queues
        for (int i = 0; i < localQueues.length; i++) {
            if (newSessionsPerEventExecutors[i] == null) {
                newSessionsPerEventExecutors[i] = new ArrayDeque<>(0);
            }
        }
        return newSessionsPerEventExecutors;
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
        var usedMinMaxCounters = this.counters;
        int used = usedMinMaxCounters.get(USED_OFFSET);
        usedMinMaxCounters.lazySet(USED_OFFSET, used);
        usedMinMaxCounters.lazySet(MIN_USED_OFFSET, used);
    }
}

package io.hyperfoil.core.session;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.AgentData;
import io.hyperfoil.api.session.GlobalData;
import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.api.session.ThreadData;
import io.hyperfoil.api.statistics.SessionStatistics;
import io.netty.util.concurrent.EventExecutor;
import io.hyperfoil.api.collection.LimitedPool;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.session.PhaseInstance;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.FormattedMessage;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

class SessionImpl implements Session {
   private static final Logger log = LogManager.getLogger(SessionImpl.class);
   private static final boolean trace = log.isTraceEnabled();

   private final Var[] vars;
   private final Map<ResourceKey<?>, Object> resources = new HashMap<>();
   private final List<Var> allVars = new ArrayList<>();
   private final List<Resource> allResources = new ArrayList<>();
   private final LimitedPool<SequenceInstance> sequencePool;
   private final SequenceInstance[] runningSequences;
   private final BitSet usedSequences;
   private final Consumer<SequenceInstance> releaseSequence = this::releaseSequence;
   private PhaseInstance phase;
   private int lastRunningSequence = -1;
   private SequenceInstance currentSequence;
   private Request currentRequest;
   private boolean scheduled;
   private boolean resetting = true;

   private EventExecutor executor;
   private ThreadData threadData;
   private AgentData agentData;
   private GlobalData globalData;
   private SessionStatistics statistics;

   private final int threadId;
   private final int uniqueId;

   private final Runnable deferredStart = this::deferredStart;

   private final Runnable runTask = this::run;

   SessionImpl(Scenario scenario, int threadId, int uniqueId) {
      this.sequencePool = new LimitedPool<>(scenario.maxSequences(), SequenceInstance::new);
      this.threadId = threadId;
      this.runningSequences = new SequenceInstance[scenario.maxSequences()];
      this.usedSequences = new BitSet(scenario.sumConcurrency());
      this.uniqueId = uniqueId;
      this.vars = scenario.createVars(this);
   }

   @Override
   public void reserve(Scenario scenario) {
      Sequence[] sequences = scenario.sequences();
      for (int i = 0; i < sequences.length; i++) {
         // We set current sequence so that we know the concurrency of current context in declareResource()
         Sequence sequence = sequences[i];
         currentSequence(sequencePool.acquire().reset(sequence, 0, null, null));
         sequence.reserve(this);
         sequencePool.release(currentSequence);
         currentSequence = null;
      }
   }

   @Override
   public Runnable runTask() {
      return runTask;
   }

   @Override
   public int uniqueId() {
      return uniqueId;
   }

   @Override
   public int agentThreadId() {
      return threadId;
   }

   @Override
   public int agentThreads() {
      return phase.agentThreads();
   }

   @Override
   public int globalThreadId() {
      return phase.agentFirstThreadId() + threadId;
   }

   @Override
   public int globalThreads() {
      Benchmark benchmark = phase.definition().benchmark();
      return benchmark.totalThreads();
   }

   @Override
   public int agentId() {
      return phase.agentId();
   }

   @Override
   public int agents() {
      return phase.definition().benchmark().agents().length;
   }

   @Override
   public String runId() {
      return phase.runId();
   }

   @Override
   public EventExecutor executor() {
      return executor;
   }

   @Override
   public ThreadData threadData() {
      return threadData;
   }

   @Override
   public AgentData agentData() {
      return agentData;
   }

   @Override
   public GlobalData globalData() {
      return globalData;
   }

   @Override
   public PhaseInstance phase() {
      return phase;
   }

   @Override
   public long phaseStartTimestamp() {
      return phase.absoluteStartTime();
   }

   void registerVar(Var var) {
      allVars.add(var);
   }

   @Override
   public <R extends Resource> void declareResource(ResourceKey<R> key, Supplier<R> resourceSupplier) {
      declareResource(key, resourceSupplier, false);
   }

   @Override
   public <R extends Resource> void declareResource(ResourceKey<R> key, Supplier<R> resourceSupplier, boolean singleton) {
      if (resources.containsKey(key)) {
         return;
      }
      // Current sequence should be null only during unit testing
      int concurrency = currentSequence == null ? 0 : currentSequence.definition().concurrency();
      if (!singleton && concurrency > 0) {
         Resource[] array = new Resource[concurrency];
         for (int i = 0; i < concurrency; ++i) {
            R resource = resourceSupplier.get();
            array[i] = resource;
            allResources.add(resource);
         }
         resources.put(key, array);
      } else {
         R resource = resourceSupplier.get();
         resources.put(key, resource);
         allResources.add(resource);
      }
   }

   @Override
   public <R extends Resource> void declareSingletonResource(ResourceKey<R> key, R resource) {
      if (resources.containsKey(key)) {
         return;
      }
      resources.put(key, resource);
      allResources.add(resource);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <R extends Resource> R getResource(ResourceKey<R> key) {
      Object res = resources.get(key);
      if (res == null) {
         return null;
      } else if (res.getClass().isArray() && res instanceof Resource[]) {
         Resource[] array = (Resource[]) res;
         return (R) array[currentSequence.index()];
      } else {
         return (R) res;
      }
   }

   @SuppressWarnings("unchecked")
   <V extends Var> V getVar(int index) {
      return (V) vars[index];
   }

   @SuppressWarnings("unchecked")
   <V extends Var> V requireSet(int index, Object key) {
      Var var = vars[index];
      if (!var.isSet()) {
         throw new IllegalStateException("Variable " + key + " was not set yet!");
      }
      return (V) var;
   }

   private void run() {
      scheduled = false;
      try {
         runSession();
      } catch (SessionStopException e) {
         log.trace("#{} Session was stopped.", uniqueId);
         // this one is OK
      } catch (Throwable t) {
         log.error(new FormattedMessage("#{} Uncaught error", uniqueId), t);
         if (phase != null) {
            phase.fail(t);
         }
      }
   }

   public void runSession() {
      if (phase.status() == PhaseInstance.Status.TERMINATED) {
         if (trace) {
            log.trace("#{} Phase is terminated", uniqueId);
         }
         return;
      }
      if (lastRunningSequence < 0) {
         if (trace) {
            log.trace("#{} No sequences to run, ignoring.", uniqueId);
         }
         return;
      }
      if (trace) {
         log.trace("#{} Run ({} running sequences)", uniqueId, lastRunningSequence + 1);
      }
      int lastProgressedSequence = -1;
      while (lastRunningSequence >= 0) {
         boolean progressed = false;
         for (int i = 0; i <= lastRunningSequence; ++i) {
            if (phase.status() == PhaseInstance.Status.TERMINATING) {
               if (trace) {
                  log.trace("#{} Phase {} is terminating", uniqueId, phase.definition().name());
               }
               stop();
               return;
            } else if (lastProgressedSequence == i) {
               break;
            }
            SequenceInstance sequence = runningSequences[i];
            if (sequence == null) {
               // This may happen when the session.stop() is called
               continue;
            }
            if (sequence.progress(this)) {
               progressed = true;
               lastProgressedSequence = i;
               if (sequence.isCompleted()) {
                  if (trace) {
                     log.trace("#{} Completed {}({})", uniqueId, sequence, sequence.index());
                  }
                  if (lastRunningSequence == -1) {
                     log.trace("#{} was stopped.", uniqueId);
                     return;
                  }
                  sequence.decRefCnt(this);
                  if (i >= lastRunningSequence) {
                     runningSequences[i] = null;
                  } else {
                     runningSequences[i] = runningSequences[lastRunningSequence];
                     runningSequences[lastRunningSequence] = null;
                  }
                  --lastRunningSequence;
                  lastProgressedSequence = -1;
               }
            }
         }
         if (!progressed && lastRunningSequence >= 0) {
            if (trace) {
               log.trace("#{} ({}) no progress, not finished.", uniqueId, phase.definition().name());
            }
            return;
         }
      }
      if (trace) {
         log.trace("#{} Session finished", uniqueId);
      }
      if (!resetting) {
         reset();
         phase.notifyFinished(this);
      }
   }

   private void releaseSequence(SequenceInstance sequence) {
      usedSequences.clear(sequence.definition().offset() + sequence.index());
      sequencePool.release(sequence);
   }


   @Override
   public void currentSequence(SequenceInstance current) {
      if (trace) {
         log.trace("#{} Changing sequence {} -> {}", uniqueId, currentSequence, current);
      }
      currentSequence = current;
   }

   public SequenceInstance currentSequence() {
      return currentSequence;
   }

   @Override
   public void attach(EventExecutor executor, ThreadData threadData, AgentData agentData, GlobalData globalData, SessionStatistics statistics) {
      assert this.executor == null;
      this.executor = executor;
      this.threadData = threadData;
      this.agentData = agentData;
      this.globalData = globalData;
      this.statistics = statistics;
   }

   @Override
   public void start(PhaseInstance phase) {
      if (trace) {
         log.trace("#{} Session starting in {}", uniqueId, phase.definition().name);
      }
      resetPhase(phase);
      executor.execute(deferredStart);
   }

   private Void deferredStart() {
      resetting = false;
      for (Sequence sequence : phase.definition().scenario().initialSequences()) {
         startSequence(sequence, false, ConcurrencyPolicy.FAIL);
      }
      run();
      return null;
   }

   @Override
   public SequenceInstance startSequence(String name, boolean forceSameIndex, ConcurrencyPolicy policy) {
      return startSequence(phase.definition().scenario().sequence(name), forceSameIndex, policy);
   }

   private SequenceInstance startSequence(Sequence sequence, boolean forceSameIndex, ConcurrencyPolicy policy) {
      int index = 0;

      if (forceSameIndex) {
         if (currentSequence == null) {
            fail(new IllegalStateException("Current sequence is not set!"));
         } else if (sequence.concurrency() != currentSequence.definition().concurrency()) {
            fail(new IllegalArgumentException("Sequence '" + sequence.name() +
                  "' does not have the same concurrency factor (" + sequence.concurrency() +
                  ") as the spawning sequence '" + currentSequence.definition().name() +
                  "' (" + currentSequence.definition().concurrency() + ")"));
         }
         index = currentSequence.index();
      }

      SequenceInstance instance = sequencePool.acquire();
      // Lookup first unused index
      for (; ; ) {
         if (sequence.concurrency() == 0) {
            if (index >= 1) {
               log.error("Cannot start sequence {} as it has already started and it is not marked as concurrent", sequence.name());
               if (sequence == currentSequence.definition()) {
                  log.info("Hint: maybe you intended only to restart the current sequence?");
               }
               sequencePool.release(instance);
               fail(new IllegalStateException("Cannot start sequence '" + sequence.name() + "' as it is not concurrent"));
            }
         } else if (index >= sequence.concurrency()) {
            if (instance != null) {
               sequencePool.release(instance);
            }
            if (policy == ConcurrencyPolicy.WARN) {
               log.warn("Cannot start sequence {}, exceeded maximum concurrency ({})", sequence.name(), sequence.concurrency());
            } else {
               log.error("Cannot start sequence {}, exceeded maximum concurrency ({})", sequence.name(), sequence.concurrency());
               fail(new IllegalStateException("Concurrency limit exceeded"));
            }
            return null;
         }
         if (!usedSequences.get(sequence.offset() + index)) {
            break;
         } else if (forceSameIndex) {
            if (policy == ConcurrencyPolicy.WARN) {
               log.warn("Cannot start sequence {} with index {} as it is already executing.", sequence.name(), index);
            } else {
               log.error("Cannot start sequence {} with index {} as it is already executing.", sequence.name(), index);
               fail(new IllegalArgumentException("Cannot start sequence with forced index."));
            }
         }
         ++index;
      }
      if (instance == null) {
         log.error("Cannot instantiate sequence {}, no free instances.", sequence.name());
         fail(new IllegalStateException("No free sequence instances"));
      } else {
         log.trace("#{} starting sequence {}({})", uniqueId(), sequence.name(), index);
         usedSequences.set(sequence.offset() + index);
         instance.reset(sequence, index, sequence.steps(), releaseSequence);

         if (lastRunningSequence >= runningSequences.length - 1) {
            throw new IllegalStateException("Maximum number of scheduled sequences exceeded!");
         }
         lastRunningSequence++;
         assert runningSequences[lastRunningSequence] == null;
         runningSequences[lastRunningSequence] = instance;
      }
      return instance;
   }

   @Override
   public void proceed() {
      if (!scheduled) {
         scheduled = true;
         executor.execute(runTask);
      }
   }

   @Override
   public Statistics statistics(int stepId, String name) {
      return statistics.getOrCreate(phase.definition(), stepId, name, phase.absoluteStartTime());
   }

   @Override
   public void pruneStats(Phase phase) {
      statistics.prune(phase);
   }

   @Override
   public void reset() {
      resetting = true;
      for (int i = 0; i < allVars.size(); ++i) {
         allVars.get(i).unset();
      }
      for (int i = 0; i < allResources.size(); i++) {
         Resource r = allResources.get(i);
         r.onSessionReset(this);
      }
      assert usedSequences.isEmpty();
      assert sequencePool.isFull();
   }

   public void resetPhase(PhaseInstance newPhase) {
      // I dislike having non-final phase but it helps not reallocating the resources...
      if (phase == newPhase) {
         return;
      }
      assert phase == null || newPhase.definition().scenario() == phase.definition().scenario();
      assert phase == null || newPhase.definition().sharedResources.equals(phase.definition().sharedResources);
      assert phase == null || phase.status().isTerminated();
      phase = newPhase;
   }

   @Override
   public void stop() {
      for (int i = 0; i <= lastRunningSequence; ++i) {
         SequenceInstance sequence = runningSequences[i];
         sequence.decRefCnt(this);
         runningSequences[i] = null;
      }
      lastRunningSequence = -1;
      currentSequence = null;
      if (trace) {
         log.trace("#{} Session stopped.", uniqueId);
      }
      if (!resetting) {
         reset();
         phase.notifyFinished(this);
      }
      throw SessionStopException.INSTANCE;
   }

   @Override
   public void fail(Throwable t) {
      log.error(new FormattedMessage("#{} Failing phase {}", uniqueId, phase.definition().name), t);
      // we need to fail the phase before stopping as stop() could cause termination
      // without recording the error on its own.
      phase.fail(t);
      stop();
   }

   @Override
   public boolean isActive() {
      return lastRunningSequence >= 0;
   }

   @Override
   public Request currentRequest() {
      return currentRequest;
   }

   @Override
   public void currentRequest(Request request) {
      this.currentRequest = request;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("#").append(uniqueId)
            .append(" (").append(phase != null ? phase.definition().name : null).append(") ")
            .append(lastRunningSequence + 1).append(" sequences:");
      for (int i = 0; i <= lastRunningSequence; ++i) {
         sb.append(' ');
         runningSequences[i].appendTo(sb);
      }
      return sb.toString();
   }

   public void destroy() {
      for (var resource : allResources) {
         resource.destroy();
      }
   }
}

package io.sailrocket.core.session;

import io.netty.util.concurrent.EventExecutor;
import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.Scenario;
import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.collection.RequestQueue;
import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.http.ValidatorResults;
import io.sailrocket.api.session.SequenceInstance;
import io.sailrocket.api.session.Session;
import io.sailrocket.api.statistics.Statistics;
import io.sailrocket.core.api.PhaseInstance;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SessionImpl implements Session, Runnable {
   private static final Logger log = LoggerFactory.getLogger(SessionImpl.class);
   private static final boolean trace = log.isTraceEnabled();

   private final HttpClientPool httpClientPool;

   // Note: HashMap.get() is allocation-free, so we can use it for direct lookups. Replacing put() is also
   // allocation-free, so vars are OK to write as long as we have them declared.
   private final Map<Object, Var> vars = new HashMap<>();
   private final Map<ResourceKey, Resource> resources = new HashMap<>();
   private final List<Var> allVars = new ArrayList<>();
   private final RequestQueueImpl requestQueue;
   private final Pool<SequenceInstance> sequencePool;
   private final SequenceInstance[] runningSequences;
   private final PhaseInstance phase;
   private int lastRunningSequence = -1;
   private SequenceInstance currentSequence;

   private EventExecutor executor;

   private final ValidatorResults validatorResults = new ValidatorResults();
   private final Statistics[] statistics;
   private final int uniqueId;

   public SessionImpl(HttpClientPool httpClientPool, PhaseInstance phase, int uniqueId) {
      Scenario scenario = phase.definition().scenario();
      this.httpClientPool = httpClientPool;
      this.requestQueue = new RequestQueueImpl(scenario.maxRequests());
      this.sequencePool = new Pool<>(scenario.maxSequences(), SequenceInstance::new);
      this.runningSequences = new SequenceInstance[scenario.maxSequences()];
      this.phase = phase;
      this.uniqueId = uniqueId;

      Sequence[] sequences = scenario.sequences();
      statistics = new Statistics[sequences.length];
      for (int i = 0; i < sequences.length; i++) {
         Sequence sequence = sequences[i];
         sequence.reserve(this);
         statistics[i] = new Statistics();
      }
      for (String var : scenario.objectVars()) {
         declare(var);
      }
      for (String var : scenario.intVars()) {
         declareInt(var);
      }
      for (Sequence sequence : scenario.initialSequences()) {
         sequence.instantiate(this, 0);
      }
   }

   @Override
   public int uniqueId() {
      return uniqueId;
   }

   @Override
   public HttpClientPool httpClientPool() {
      return httpClientPool;
   }

   @Override
   public EventExecutor executor() {
      return executor;
   }

   @Override
   public Phase phase() {
      return phase.definition();
   }

   void registerVar(Var var) {
      allVars.add(var);
   }

   @Override
   public Session declare(Object key) {
      ObjectVar var = new ObjectVar(this);
      vars.put(key, var);
      return this;
   }

   @Override
   public Object getObject(Object key) {
      return ((ObjectVar) requireSet(key, vars.get(key))).get();
   }

   @Override
   public Session setObject(Object key, Object value) {
      if (trace) {
         log.trace("#{} {} <- {}", uniqueId, key, value);
      }
      ObjectVar wrapper = (ObjectVar) vars.get(key);
      wrapper.value = value;
      wrapper.set = true;
      return this;
   }

   @Override
   public Session declareInt(Object key) {
      IntVar var = new IntVar(this);
      vars.put(key, var);
      return this;
   }

   @Override
   public int getInt(Object key) {
      IntVar var = (IntVar) vars.get(key);
      if (!var.isSet()) {
         throw new IllegalStateException("Variable " + key + " was not set yet!");
      }
      return var.get();
   }

   @Override
   public Session setInt(Object key, int value) {
      if (trace) {
         log.trace("#{} {} <- {}", uniqueId, key, value);
      }
      ((IntVar) vars.get(key)).set(value);
      return this;
   }

   @Override
   public Session addToInt(Object key, int delta) {
      IntVar wrapper = (IntVar) vars.get(key);
      if (!wrapper.isSet()) {
         throw new IllegalStateException("Variable " + key + " was not set yet!");
      }
      log.trace("#{} {} <- {}", uniqueId, key, wrapper.get() + delta);
      wrapper.set(wrapper.get() + delta);
      return this;
   }

   @Override
   public boolean isSet(Object key) {
      return vars.get(key).isSet();
   }

   @Override
   public Object activate(Object key) {
      ObjectVar var = (ObjectVar) vars.get(key);
      var.set = true;
      return var.get();
   }

   @Override
   public Session unset(Object key) {
      vars.get(key).unset();
      return this;
   }

   @Override
   public <R extends Resource> void declareResource(ResourceKey<R> key, R resource) {
      resources.put(key, resource);
   }

   @Override
   public <R extends Resource> R getResource(ResourceKey<R> key) {
      return (R) resources.get(key);
   }

   private <W extends Var> W requireSet(Object key, W wrapper) {
      if (!wrapper.isSet()) {
         throw new IllegalStateException("Variable " + key + " was not set yet!");
      }
      return wrapper;
   }

   public void run() {
      if (phase.status() == PhaseInstance.Status.TERMINATED ) {
         log.trace("called run on terminated session #{}", uniqueId);
         return;
      }
      log.trace("run session #{}", uniqueId);
      int lastProgressedSequence = -1;
      while (lastRunningSequence >= 0) {
         boolean progressed = false;
         for (int i = 0; i <= lastRunningSequence; ++i) {
            if (phase.status() == PhaseInstance.Status.TERMINATING) {
               if (trace) {
                  log.trace("#{} Phase {} is terminating", uniqueId, phase.definition().name());
               }
               phase.notifyTerminated(this);
               return;
            } else if (lastProgressedSequence == i) {
               break;
            }
            currentSequence(runningSequences[i]);
            if (runningSequences[i].progress(this)) {
               progressed = true;
               lastProgressedSequence = i;
               if (runningSequences[i].isCompleted()) {
                  sequencePool.release(runningSequences[i]);
                  if (i == lastRunningSequence) {
                     runningSequences[i] = null;
                  } else {
                     runningSequences[i] = runningSequences[lastRunningSequence];
                     runningSequences[lastRunningSequence] = null;
                  }
                  --lastRunningSequence;
                  lastProgressedSequence = -1;
               }
            }
            currentSequence(null);
         }
         if (!progressed && lastRunningSequence >= 0) {
            log.trace("#{} ({}) no progress, not finished.", uniqueId, phase.definition().name());
            return;
         }
      }
      log.trace("notifyFinished session #{}", uniqueId);
      phase.notifyFinished(this);
   }

   @Override
   public void currentSequence(SequenceInstance current) {
      log.trace("#{} Changing sequence {} -> {}", uniqueId, currentSequence, current);
      assert current == null || currentSequence == null;
      currentSequence = current;
   }

   public SequenceInstance currentSequence() {
      return currentSequence;
   }

   @Override
   public void proceed(EventExecutor executor) {
      assert this.executor == null || this.executor == executor;
      this.executor = executor;
      executor.submit(this);
   }

   @Override
   public ValidatorResults validatorResults() {
      return validatorResults;
   }

   @Override
   public Statistics statistics(int sequenceId) {
      return statistics[sequenceId];
   }

   @Override
   public Statistics[] statistics() {
      return statistics;
   }

   @Override
   public void reset() {
      sequencePool.checkFull();
      for (int i = 0; i < allVars.size(); ++i) {
         allVars.get(i).unset();
      }
      executor = null;
      for (Sequence sequence : phase.definition().scenario().initialSequences()) {
         sequence.instantiate(this, 0);
      }
   }

   @Override
   public void nextSequence(String name) {
      phase.definition().scenario().sequence(name).instantiate(this, 0);
   }

   @Override
   public void stop() {
      for (int i = 0; i <= lastRunningSequence; ++i) {
         sequencePool.release(runningSequences[i]);
         runningSequences[i] = null;
      }
      lastRunningSequence = -1;
   }

   @Override
   public void fail(Throwable t) {
      stop();
      phase.fail(t);
   }

   @Override
   public RequestQueue requestQueue() {
      return requestQueue;
   }

   public SequenceInstance acquireSequence() {
      return sequencePool.acquire();
   }

   public void enableSequence(SequenceInstance instance) {
      if (lastRunningSequence >= runningSequences.length - 1) {
         throw new IllegalStateException("Maximum number of scheduled sequences exceeded!");
      }
      lastRunningSequence++;
      assert runningSequences[lastRunningSequence] == null;
      runningSequences[lastRunningSequence] = instance;
   }
}

package io.sailrocket.core.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.SequenceStatistics;
import io.sailrocket.core.client.ValidatorResults;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Session implements io.sailrocket.api.Session, Runnable {
   private static final Logger log = LoggerFactory.getLogger(Session.class);
   private static final boolean trace = log.isTraceEnabled();

   private final HttpClientPool httpClientPool;
   private final ScheduledExecutorService scheduledExecutor;

   // Note: HashMap.get() is allocation-free, so we can use it for direct lookups. Replacing put() is also
   // allocation-free, so vars are OK to write as long as we have them declared.
   private final Map<Object, Var> vars = new HashMap<>();
   private final Map<ResourceKey, Resource> resources = new HashMap<>();
   private final List<Var> allVars = new ArrayList<>();
   private final RequestQueue requestQueue;
   private final Pool<SequenceInstance> sequencePool;
   private final SequenceInstance[] runningSequences;
   private int lastRunningSequence = -1;
   private SequenceInstance currentSequence;

   private final ValidatorResults validatorResults = new ValidatorResults();
   private final SequenceStatistics statistics = new SequenceStatistics();

   private final Runnable run = this::run;

   public Session(HttpClientPool httpClientPool, ScheduledExecutorService scheduledExecutor, int maxConcurrency, int maxScheduledSequences) {
      this.httpClientPool = httpClientPool;
      this.scheduledExecutor = scheduledExecutor;
      this.requestQueue = new RequestQueue(maxConcurrency);
      this.sequencePool = new Pool<>(maxConcurrency, SequenceInstance::new);
      this.runningSequences = new SequenceInstance[maxScheduledSequences];
   }

   HttpClientPool getHttpClientPool() {
      return httpClientPool;
   }

   // TODO: we should execute all action on the same thread, so it would be better to delegate this to the event loop used by HttpClientPool
   ScheduledExecutorService getScheduledExecutor() {
      return scheduledExecutor;
   }

   public void registerVar(Var var) {
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
         log.trace("{} <- {}", key, value);
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
         log.trace("{} <- {}", key, value);
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
      log.trace("{} <- {}", key, wrapper.get() + delta);
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
   public void deactivate(Object key) {
      vars.get(key).unset();
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
      while (lastRunningSequence >= 0) {
         boolean progressed = false;
         for (int i = 0; i <= lastRunningSequence; ++i) {
            currentSequence(runningSequences[i]);
            if (runningSequences[i].progress(this)) {
               progressed = true;
               if (runningSequences[i].isCompleted()) {
                  sequencePool.release(runningSequences[i]);
                  if (i == lastRunningSequence) {
                     runningSequences[i] = null;
                  } else {
                     runningSequences[i] = runningSequences[lastRunningSequence];
                     runningSequences[lastRunningSequence] = null;
                  }
                  --lastRunningSequence;
               }
            }
            currentSequence(null);
         }
         if (!progressed) {
            break;
         }
      }
   }

   void currentSequence(SequenceInstance current) {
      log.trace("Changing sequence {} -> {}", currentSequence, current);
      assert current == null || currentSequence == null;
      currentSequence = current;
   }

   public SequenceInstance currentSequence() {
      return currentSequence;
   }

   public ValidatorResults validatorResults() {
      return validatorResults;
   }

   public SequenceStatistics statistics() {
      return statistics;
   }

   void reset() {
      sequencePool.checkFull();
      for (int i = 0; i < allVars.size(); ++i) {
         allVars.get(i).unset();
      }
      // TODO should we reset stats here?
   }

   RequestQueue requestQueue() {
      return requestQueue;
   }

   Runnable progress() {
      return run;
   }

   public SequenceInstance acquireSequence() {
      return sequencePool.acquire();
   }

   public void enableSequence(SequenceInstance instance) {
      lastRunningSequence++;
      assert lastRunningSequence < runningSequences.length && runningSequences[lastRunningSequence] == null;
      runningSequences[lastRunningSequence] = instance;
   }
}

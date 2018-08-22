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

public class Session implements io.sailrocket.api.Session {
   private static final Logger log = LoggerFactory.getLogger(Session.class);
   private static final boolean trace = log.isTraceEnabled();

   private final HttpClientPool httpClientPool;
   private final ScheduledExecutorService scheduledExecutor;

   private State currentState;
   // Note: HashMap.get() is allocation-free, so we can use it for direct lookups. Replacing put() is also
   // allocation-free, so vars are OK to write as long as we have them declared.
   private final Map<Object, Var> vars = new HashMap<>();
   private final Map<ResourceKey, Resource> resources = new HashMap<>();
   private final List<Var> allVars = new ArrayList<>();
   private final RequestQueue requestQueue;

   private final ValidatorResults validatorResults = new ValidatorResults();
   private final SequenceStatistics statistics = new SequenceStatistics();

   private final Runnable run = this::run;

   public Session(HttpClientPool httpClientPool, ScheduledExecutorService scheduledExecutor, State initState, int maxConcurrentRequests) {
      this.httpClientPool = httpClientPool;
      this.scheduledExecutor = scheduledExecutor;
      this.currentState = initState;
      this.requestQueue = new RequestQueue(maxConcurrentRequests);
   }

   HttpClientPool getHttpClientPool() {
      return httpClientPool;
   }

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

   void setState(State newState) {
      if (trace) {
         log.trace("Traversing {} -> {}", this.currentState, newState);
      }
      this.currentState = newState;
   }

   public void run() {
      while (currentState != null && currentState.progress(this));
   }

   public ValidatorResults validatorResults() {
      return validatorResults;
   }

   public SequenceStatistics statistics() {
      return statistics;
   }

   void reset(State state) {
      this.currentState = state;
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
}

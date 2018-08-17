package io.sailrocket.core.machine;

import java.util.HashMap;
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
   private final Map<Object, Object> vars = new HashMap<>();
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

   @Override
   public Session declare(Object key) {
      vars.put(key, null);
      return this;
   }

   @Override
   public Object getObject(Object key) {
      return vars.get(key);
   }

   @Override
   public Session setObject(Object key, Object value) {
      if (trace) {
         log.trace("{} <- {}", key, value);
      }
      vars.put(key, value);
      return this;
   }

   @Override
   public Session declareInt(Object key) {
      vars.put(key, new IntWrapper());
      return this;
   }

   @Override
   public int getInt(Object key) {
      return ((IntWrapper) vars.get(key)).value;
   }

   @Override
   public Session setInt(Object key, int value) {
      if (trace) {
         log.trace("{} <- {}", key, value);
      }
      ((IntWrapper) vars.get(key)).value = value;
      return this;
   }

   @Override
   public Session addToInt(Object key, int delta) {
      IntWrapper wrapper = (IntWrapper) vars.get(key);
      log.trace("{} <- {}", key, wrapper.value + delta);
      wrapper.value += delta;
      return this;
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
      // TODO should we reset stats here?
   }

   RequestQueue requestQueue() {
      return requestQueue;
   }

   Runnable progress() {
      return run;
   }

   private static class IntWrapper {
      int value;
   }
}

package io.sailrocket.core.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.core.client.ValidatorResults;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Session implements io.sailrocket.api.Session {
   private static final Logger log = LoggerFactory.getLogger(Session.class);

   private final HttpClientPool httpClientPool;
   private final ScheduledExecutorService scheduledExecutor;

   private State currentState;
   // Note: HashMap.get() is allocation-free, so we can use it for direct lookups. Replacing put() is also
   // allocation-free, so vars are OK to write as long as we have them declared.
   private Map<Object, Object> vars = new HashMap<>();
   private Map<State, Map<String, Object>> handlers = new HashMap<>();

   private final ValidatorResults validatorResults = new ValidatorResults();

   public Session(HttpClientPool httpClientPool, ScheduledExecutorService scheduledExecutor, State initState) {
      this.httpClientPool = httpClientPool;
      this.scheduledExecutor = scheduledExecutor;
      this.currentState = initState;
   }

   HttpClientPool getHttpClientPool() {
      return httpClientPool;
   }

   ScheduledExecutorService getScheduledExecutor() {
      return scheduledExecutor;
   }

   void registerIntHandler(State state, String type, IntConsumer handler) {
      handlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   void registerExceptionHandler(State state, String type, Consumer<Throwable> handler) {
      handlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   void registerObjectHandler(State state, String type, Consumer<Object> handler) {
      handlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   void registerVoidHandler(State state, String type, Runnable handler) {
      handlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   void registerBiHandler(State state, String type, BiConsumer<Object, Object> handler) {
      handlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   IntConsumer intHandler(State state, String type) {
      // handler not registered is a bug
      return (IntConsumer) handlers.get(state).get(type);
   }

   Consumer<Throwable> exceptionHandler(State state, String type) {
      return (Consumer<Throwable>) handlers.get(state).get(type);
   }

   <T> Consumer<T> objectHandler(State state, String type) {
      return (Consumer<T>) handlers.get(state).get(type);
   }

   Runnable voidHandler(State state, String type) {
      return (Runnable) handlers.get(state).get(type);
   }

   <A, B> BiConsumer<A, B> biHandler(State state, String type) {
      return (BiConsumer<A, B>) handlers.get(state).get(type);
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
      log.trace("{} <- {}", key, value);
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
      log.trace("{} <- {}", key, value);
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
      log.trace("Traversing {} -> {}", this.currentState, newState);
      this.currentState = newState;
   }

   public void run() {
      while (currentState != null && currentState.progress(this));
   }

   public ValidatorResults validatorResults() {
      return validatorResults;
   }

   void reset(State state) {
      this.currentState = state;
   }

   private static class IntWrapper {
      int value;
   }
}

package io.sailrocket.core.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.sailrocket.api.HttpClientPool;
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
   private Map<State, Map<String, IntConsumer>> intHandlers = new HashMap<>();
   private Map<State, Map<String, Consumer<Throwable>>> exceptionHandlers = new HashMap<>();
   private Map<State, Map<String, Consumer<Object>>> objectHandlers = new HashMap<>();
   private Map<State, Map<String, Runnable>> voidHandlers = new HashMap<>();

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
      intHandlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   void registerExceptionHandler(State state, String type, Consumer<Throwable> handler) {
      exceptionHandlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   void registerObjectHandler(State state, String type, Consumer<Object> handler) {
      objectHandlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   void registerVoidHandler(State state, String type, Runnable handler) {
      voidHandlers.computeIfAbsent(state, s -> new HashMap<>()).put(type, handler);
   }

   IntConsumer intHandler(State state, String type) {
      // handler not registered is a bug
      return intHandlers.get(state).get(type);
   }

   Consumer<Throwable> exceptionHandler(State state, String type) {
      return exceptionHandlers.get(state).get(type);
   }

   <T> Consumer<T> objectHandler(State state, String type) {
      return (Consumer<T>) objectHandlers.get(state).get(type);
   }

   Runnable voidHandler(State state, String type) {
      return voidHandlers.get(state).get(type);
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
   public int getInt(Object key) {
      return ((IntWrapper) vars.get(key)).value;
   }

   @Override
   public Session setInt(Object key, int value) {
      log.trace("{} <- {}", key, value);
      ((IntWrapper) vars.computeIfAbsent(key, k -> new IntWrapper())).value = value;
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

   private static class IntWrapper {
      int value;
   }
}

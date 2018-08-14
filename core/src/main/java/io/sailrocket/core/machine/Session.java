package io.sailrocket.core.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.sailrocket.api.HttpClientPool;

public class Session {
   private final HttpClientPool httpClientPool;
   private final ScheduledExecutorService scheduledExecutor;

   private State currentState;
   // Note: HashMap.get() is allocation-free, so we can use it for direct lookups. Replacing put() is also
   // allocation-free, so vars are OK to write as long as we have them declared.
   private Map<String, Object> vars = new HashMap<>();
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

   public Object var(String name) {
      return vars.get(name);
   }

   public void var(String name, Object value) {
      vars.put(name, value);
   }

   void setState(State currentState) {
      this.currentState = currentState;
   }

   public void run() {
      while (currentState.progress(this));
   }
}

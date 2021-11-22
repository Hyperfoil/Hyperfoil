package io.hyperfoil.core.session;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.session.GlobalData;
import io.netty.channel.EventLoop;

public class GlobalDataImpl implements GlobalData {
   private static final Logger log = LogManager.getLogger(GlobalDataImpl.class);
   private static final PoisonedQueue POISON = new PoisonedQueue();

   private final EventLoop executor;
   private final Map<String, String> publishingPhase = new HashMap<>();
   private final Map<String, GlobalData.Element> published = new HashMap<>();
   private final Map<String, Queue<GlobalData.Element>> toPublish = new HashMap<>();

   public GlobalDataImpl(EventLoop executor) {
      this.executor = executor;
   }

   @Override
   public void publish(String phase, String key, Element element) {
      assert executor.inEventLoop();
      String otherPhase = publishingPhase.put(key, phase);
      if (otherPhase != null && !otherPhase.equals(phase)) {
         throw new IllegalStateException("Global record for key '" + key + "' is published by phase '" + phase + "', no other phase can publish it.");
      }
      Queue<Element> queue = toPublish.computeIfAbsent(key, k -> new ArrayDeque<>());
      if (queue == POISON) {
         throw new IllegalStateException("Global record for key '" + key + "' has already been published; cannot add any more records.");
      }
      queue.add(element);
   }

   @Override
   public Element read(String key) {
      assert executor.inEventLoop();
      Element element = published.get(key);
      if (element == null) {
         throw new IllegalStateException("Cannot retrieve global record for key '" + key + "' - probably it was not published yet. Make sure the publishing phase and this phase are strictly ordered.");
      }
      return element;
   }

   public GlobalData.Element extractOne(String key) {
      Queue<Element> queue = toPublish.get(key);
      if (queue == null || queue.isEmpty()) {
         return null;
      }
      Element first = queue.remove();
      if (queue.isEmpty()) {
         return first;
      }
      Accumulator accumulator = first.newAccumulator();
      accumulator.add(first);
      Element next;
      while ((next = queue.poll()) != null) {
         accumulator.add(next);
      }
      toPublish.put(key, POISON);
      return accumulator.complete();
   }

   public void add(Map<String, Element> data) {
      assert executor.inEventLoop();
      for (var entry : data.entrySet()) {
         GlobalData.Element prev = published.put(entry.getKey(), entry.getValue());
         if (prev != null) {
            log.error("Global data for key {} has been overridden: previous: {}, new: {}", entry.getKey(), prev, entry.getValue());
            assert false;
         }
      }
   }

   public static class Collector {
      private final Map<String, GlobalData.Accumulator> accumulators = new HashMap<>();

      public void collect(String phase, GlobalDataImpl data) {
         assert data.executor.inEventLoop();
         for (var entry : data.publishingPhase.entrySet()) {
            if (!phase.equals(entry.getValue())) {
               continue;
            }

            Element reduced = data.extractOne(entry.getKey());
            synchronized (this) {
               GlobalData.Accumulator accumulator = accumulators.get(entry.getKey());
               if (accumulator == null) {
                  accumulator = reduced.newAccumulator();
                  accumulators.put(entry.getKey(), accumulator);
               }
               accumulator.add(reduced);
            }
         }
      }

      public synchronized Map<String, GlobalData.Element> extract() {
         return accumulators.entrySet().stream()
               .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().complete()));
      }
   }

   private static class PoisonedQueue extends AbstractQueue<Element> {
      @Override
      public Iterator<Element> iterator() {
         throw new UnsupportedOperationException();
      }

      @Override
      public int size() {
         return 0;
      }

      @Override
      public boolean offer(Element element) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Element poll() {
         throw new UnsupportedOperationException();
      }

      @Override
      public Element peek() {
         throw new UnsupportedOperationException();
      }
   }
}

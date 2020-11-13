package io.hyperfoil.core.data;

import java.util.Arrays;
import java.util.Objects;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.ObjectVar;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Queue implements Session.Resource {
   private static final Logger log = LoggerFactory.getLogger(Queue.class);
   private static final boolean trace = log.isTraceEnabled();

   private final Access var;
   private final Object[] data;
   private final int concurrency;
   private final String sequence;
   private final Action onCompletion;

   private int head, tail, active, size;
   private boolean producerComplete;

   public Queue(Access var, int size, int concurrency, String sequence, Action onCompletion) {
      if (var.isSequenceScoped()) {
         throw new BenchmarkDefinitionException("Queue variable should not be sequence-scoped for queue; use sequence-scoped access only for reading.");
      }
      this.var = var;
      this.data = new Object[size];

      this.concurrency = concurrency;
      this.sequence = sequence;
      this.onCompletion = onCompletion;
   }

   public int concurrency() {
      return concurrency;
   }

   @Override
   public void onSessionReset() {
      reset();
   }

   public void reset() {
      // When the session is stopped there might be active sequences
      active = 0;
      head = 0;
      tail = 0;
      size = 0;
      producerComplete = false;
      Arrays.fill(data, null);
   }

   public void push(Session session, Object value) {
      log.trace("#{} adding {} to queue -> {}", session.uniqueId(), value, var);
      Objects.requireNonNull(value);
      if (size < data.length) {
         data[tail++] = value;
         if (tail >= data.length) {
            tail = 0;
         }
         ++size;
      } else {
         // TODO: add some stats for this? Or fail the session?
         log.error("#{} Exceeded maximum size of queue {} ({}), dropping value {}", session.uniqueId(), var, data.length, value);
      }
      if (active < concurrency && size > 0) {
         ++active;
         --size;
         Object queuedValue = data[head];
         data[head++] = null;
         if (head >= data.length) {
            head = 0;
         }
         SequenceInstance instance = session.startSequence(sequence, false, Session.ConcurrencyPolicy.FAIL);
         if (trace) {
            log.trace("#{} starting {} with queued value {} in {}[{}]", session.uniqueId(), sequence, queuedValue, var, instance.index());
         }
         ObjectVar[] output = (ObjectVar[]) var.getObject(session);
         output[instance.index()].set(queuedValue);
      }
   }

   public void producerComplete(Session session) {
      log.trace("#{} producer of {} is complete", session.uniqueId(), var);
      this.producerComplete = true;
      if (active == 0) {
         complete(session);
      }
   }

   public void consumed(Session session) {
      SequenceInstance instance = session.currentSequence();
      if (trace) {
         log.trace("#{} consumed {}[{}], head={}, tail={}", session.uniqueId(), var, instance.index(), head, tail);
      }
      if (head < tail) {
         Object queuedValue = data[head];
         data[head++] = null;
         // This is supposed to be invoked only as the last step of the sequence used in this queue
         assert instance.definition().name().equals(sequence);
         ObjectVar[] output = (ObjectVar[]) var.getObject(session);
         output[instance.index()].set(queuedValue);
         if (trace) {
            log.trace("#{} restarting sequence {}[{}] with {} -> {}", sequence, instance.index(), queuedValue, var);
         }
         // We are not starting a new sequence because we'd need the concurrency maximum for given sequence to be
         // +1 (as both this and the new sequence would be running at the same time).
         instance.restart(session);
      } else {
         --active;
         if (producerComplete && active == 0) {
            complete(session);
         }
      }
   }

   private void complete(Session session) {
      assert head == tail;
      log.trace("#{} queue {} completed", session.uniqueId(), var);
      reset();
      if (onCompletion != null) {
         onCompletion.run(session);
      }
   }

   public static class Key implements Session.ResourceKey<Queue> {}
}

package io.hyperfoil.core.data;

import java.util.Arrays;
import java.util.Objects;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.ObjectVar;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Queue implements Session.Resource {
   private static final Logger log = LoggerFactory.getLogger(Queue.class);

   private final Access var;
   private final Object[] data;
   private final int concurrency;
   private final String sequence;
   private final Action onCompletion;

   private int head, tail, active;
   private boolean producerComplete;

   public Queue(Access var, int size, int concurrency, String sequence, Action onCompletion) {
      this.var = var;
      this.data = new Object[size];

      this.concurrency = concurrency;
      this.sequence = sequence;
      this.onCompletion = onCompletion;
   }

   public void reset() {
      head = 0;
      tail = 0;
      producerComplete = false;
      Arrays.fill(data, null);
   }

   public void push(Session session, Object value) {
      log.trace("#{} adding {} to {}", session.uniqueId(), value, var);
      Objects.requireNonNull(value);
      if (tail < data.length) {
         data[tail++] = value;
      } else {
         // TODO: add some stats for this? Or fail the session?
         log.warn("Exceed maximum size of queue {} ({}), dropping value {}", var, data.length, value);
      }
      if (active < concurrency && head < tail) {
         ++active;
         Object queuedValue = data[head];
         data[head++] = null;
         SequenceInstance instance = session.startSequence(sequence, Session.ConcurrencyPolicy.FAIL);
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
      log.trace("#{} consumed {}[{}], head={}, tail={}", session.uniqueId(), var, instance.index(), head, tail);
      if (head < tail) {
         Object queuedValue = data[head];
         data[head++] = null;
         // This is supposed to be invoked only as the last step of the sequence used in this queue
         assert instance.definition().name().equals(sequence);
         ObjectVar[] output = (ObjectVar[]) var.getObject(session);
         output[instance.index()].set(queuedValue);
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
      onCompletion.run(session);
   }
}

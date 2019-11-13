package io.hyperfoil.core.data;

import java.util.Objects;

import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.ObjectVar;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Queue {
   private static final Logger log = LoggerFactory.getLogger(Queue.class);

   private final String var;
   private final ObjectVar[] elements;
   private final int concurrency;
   private final String sequence;
   private final Action onCompletion;
   ;

   private int head, tail, active;
   private boolean producerComplete;

   public Queue(Session session, String var, int size, int concurrency, String sequence, Action onCompletion) {
      this.var = var;
      this.elements = ObjectVar.newArray(session, size);

      this.concurrency = concurrency;
      this.sequence = sequence;
      this.onCompletion = onCompletion;
   }

   public void reset() {
      head = 0;
      tail = 0;
      producerComplete = false;
      for (ObjectVar var : elements) {
         var.unset();
      }
   }

   public void push(Session session, Object value) {
      log.trace("#{} adding {} to {}", session.uniqueId(), value, var);
      Objects.requireNonNull(value);
      if (tail < elements.length) {
         elements[tail++].set(value);
      } else {
         // TODO: add some stats for this? Or fail the session?
         log.warn("Exceed maximum size of queue {} ({}), dropping value {}", var, elements.length, value);
      }
      if (active < concurrency && head < tail) {
         ++active;
         session.phase().scenario().sequence(sequence).instantiate(session, head++);
      }
   }

   public void producerComplete(Session session) {
      log.trace("#{} producer of {} is complete", session.uniqueId(), var);
      this.producerComplete = true;
   }

   public void consumed(Session session, int index) {
      log.trace("#{} consumed {}[{}]", session.uniqueId(), var, index);
      assert elements[index].isSet();
      elements[index].unset();
      if (head < tail) {
         session.phase().scenario().sequence(sequence).instantiate(session, head++);
      } else {
         --active;
         if (producerComplete && active == 0) {
            assert head == tail;
            log.trace("#{} queue {} completed", session.uniqueId(), var);
            onCompletion.run(session);
         }
      }
   }

   public ObjectVar get(int index) {
      return elements[index];
   }
}

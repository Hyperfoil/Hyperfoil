package io.sailrocket.core.machine;

import java.util.Arrays;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * A state where we await some external event, e.g. HTTP response received. We might be waiting for any of several
 * events (e.g. successful response vs. connection error).
 */
public class State {
   protected static final Logger log = LoggerFactory.getLogger(State.class);

   static final String PROGRESS = "progress";

   // Just for debugging purposes
   private final String name;
   private Transition[] transitions;

   public State(String name) {
      this.name = name;
   }

   public boolean progress(Session session) {
      for (int i = 0; i < transitions.length; ++i) {
         if (transitions[i].test(session)) {
            session.setState(transitions[i].invoke(session));
            return !transitions[i].isBlocking();
         }
      }
      throw new IllegalStateException();
   }

   public void addTransition(Transition t) {
      if (transitions == null) {
         transitions = new Transition[] { t };
      } else {
         transitions = Arrays.copyOf(transitions, transitions.length + 1);
         transitions[transitions.length - 1] = t;
      }
   }

   public void register(Session session) {
      session.registerVoidHandler(this, PROGRESS, () -> session.run());
   }

   @Override
   public String toString() {
      return getClass().getSimpleName() + "/" + name;
   }
}

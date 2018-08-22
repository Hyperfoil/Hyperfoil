package io.sailrocket.core.machine;

import java.util.Arrays;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * A state where we await some external event, e.g. HTTP response received. We might be waiting for any of several
 * events (e.g. successful response vs. connection error).
 */
public class State {
   private static final Logger log = LoggerFactory.getLogger(State.class);
   private static final boolean trace = log.isTraceEnabled();

   // Just for debugging purposes
   private final String name;
   private VarReference[] varReferences;
   private Transition[] transitions;

   public State(String name) {
      this.name = name;
   }

   boolean progress(Session session) {
      if (varReferences != null) {
         for (VarReference ref : varReferences) {
            if (!ref.isSet(session)) {
               log.trace("State {} cannot follow any transition because it's blocked by missing var reference {}", name, ref);
               return false;
            }
         }
      }
      for (int i = 0; i < transitions.length; ++i) {
         if (transitions[i].test(session)) {
            if (trace) {
               log.trace("Preparing transition {}/{}", name, i);
            }
            if (transitions[i].prepare(session)) {
               if (trace) {
                  log.trace("Following transition {}/{}", name, i);
               }
               session.setState(transitions[i].invoke(session));
               return true;
            } else {
               if (trace) {
                  log.trace("{} blocking because of failed prepare in transition {}", name, i);
               }
               return false;
            }
         }
      }
      throw new IllegalStateException("In state " + name);
   }

   public void addDependency(VarReference ref) {
      if (varReferences == null) {
         varReferences = new VarReference[] { ref };
      } else {
         varReferences = Arrays.copyOf(varReferences, varReferences.length + 1);
         varReferences[varReferences.length - 1] = ref;
      }
   }

   public void addTransition(Transition t) {
      if (transitions == null) {
         transitions = new Transition[] { t };
      } else {
         transitions = Arrays.copyOf(transitions, transitions.length + 1);
         transitions[transitions.length - 1] = t;
      }
   }

   public void reserve(Session session) {
      for (Transition t : transitions) {
         t.reserve(session);
      }
   }

   @Override
   public String toString() {
      return getClass().getSimpleName() + "/" + name;
   }
}

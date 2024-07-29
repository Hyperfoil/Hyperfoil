package io.hyperfoil.core.session;

import java.util.Arrays;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;

class SequenceScopedObjectAccess extends SequenceScopedReadAccess implements ObjectAccess {
   SequenceScopedObjectAccess(Object key, int maxConcurrency) {
      super(key, maxConcurrency);
   }

   @Override
   public Session.Var createVar(Session session, Session.Var existing) {
      // When a step/action sets a variable, it doesn't know if that's a global or sequence-scoped
      // and must declare it, just in case.
      SessionImpl impl = (SessionImpl) session;
      if (existing == null) {
         existing = new ObjectVar(impl);
      }
      if (!(existing instanceof ObjectVar)) {
         throw new BenchmarkDefinitionException(
               "Variable " + key + " should hold an object but it is defined to hold an integer elsewhere.");
      }
      Object contents = existing.objectValue(session);
      if (contents == null) {
         ((ObjectVar) existing).set(ObjectVar.newArray(session, maxConcurrency));
      } else if (contents instanceof ObjectVar[]) {
         ObjectVar[] oldArray = (ObjectVar[]) contents;
         if (oldArray.length < maxConcurrency) {
            ObjectVar[] newArray = Arrays.copyOf(oldArray, maxConcurrency);
            for (int i = oldArray.length; i < newArray.length; ++i) {
               newArray[i] = new ObjectVar(impl);
            }
            ((ObjectVar) existing).set(newArray);
         }
      } else {
         throw new BenchmarkDefinitionException(
               "Unexpected content in " + key + ": should hold array of ObjectVar but holds " + contents);
      }
      return existing;
   }

   @Override
   public void setObject(Session session, Object value) {
      ObjectVar var = getVarToSet(session);
      Object o = getItemFromVar(session, var);

      if (o instanceof ObjectVar) {
         if (trace) {
            log.trace("#{} {}[{}] <- {}", session.uniqueId(), key, session.currentSequence().index(), value);
         }
         ((ObjectVar) o).set(value);
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain ObjectVar but contains " + o);
      }
   }

   @Override
   public Object activate(Session session) {
      Object o = getItem(session);

      if (o instanceof ObjectVar) {
         ObjectVar ov = (ObjectVar) o;
         if (trace) {
            log.trace("#{} activate {}[{}]", session.uniqueId(), key, session.currentSequence().index());
         }
         ov.set = true;
         return ov.objectValue(session);
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain ObjectVar but contains " + o);
      }
   }

   @Override
   public void unset(Session session) {
      ObjectVar var = getVarToSet(session);
      Object o = getItemFromVar(session, var);

      if (o instanceof ObjectVar) {
         if (trace) {
            log.trace("#{} unset {}[{}]", session.uniqueId(), key, session.currentSequence().index());
         }
         ((ObjectVar) o).unset();
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException(
               "Variable " + key + "[" + index + "] should contain Session.Var(Object) but contains " + o);
      }
   }

}

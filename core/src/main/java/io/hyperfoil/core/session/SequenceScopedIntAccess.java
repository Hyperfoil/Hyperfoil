package io.hyperfoil.core.session;

import java.util.Arrays;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;

class SequenceScopedIntAccess extends SequenceScopedReadAccess implements IntAccess {
   SequenceScopedIntAccess(Object key, int maxConcurrency) {
      super(key, maxConcurrency);
   }

   @Override
   public Session.Var createVar(Session session, Session.Var existing) {
      SessionImpl impl = (SessionImpl) session;
      if (existing == null) {
         existing = new ObjectVar(impl);
      } else if (!(existing instanceof ObjectVar)) {
         throw new BenchmarkDefinitionException(
               "Variable " + key + " should hold an object but it is defined to hold an integer elsewhere.");
      }
      Object contents = existing.objectValue(session);
      if (contents == null) {
         ((ObjectVar) existing).set(IntVar.newArray(session, maxConcurrency));
      } else if (contents instanceof IntVar[]) {
         IntVar[] oldArray = (IntVar[]) contents;
         if (oldArray.length < maxConcurrency) {
            IntVar[] newArray = Arrays.copyOf(oldArray, maxConcurrency);
            for (int i = oldArray.length; i < newArray.length; ++i) {
               newArray[i] = new IntVar(impl);
            }
            ((ObjectVar) existing).set(newArray);
         }
      } else {
         throw new BenchmarkDefinitionException(
               "Unexpected content in " + key + ": should hold array of IntVar but holds " + contents);
      }
      return existing;
   }

   @Override
   public void setInt(Session session, int value) {
      ObjectVar var = getVarToSet(session);
      Object o = getItemFromVar(session, var);

      if (o instanceof IntVar) {
         if (trace) {
            log.trace("#{} {}[{}] <- {}", session.uniqueId(), key, session.currentSequence().index(), value);
         }
         ((IntVar) o).set(value);
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain IntVar but contains " + o);
      }
   }

   @Override
   public int addToInt(Session session, int delta) {
      Object o = getItem(session);

      if (o instanceof IntVar) {
         IntVar iv = (IntVar) o;
         if (!iv.isSet()) {
            int index = session.currentSequence().index();
            throw new IllegalStateException("Variable " + key + "[" + index + "] was not set yet!");
         }
         int prev = iv.intValue(session);
         if (trace) {
            log.trace("#{} {}[{}] += {}", session.uniqueId(), key, session.currentSequence().index(), delta);
         }
         iv.add(delta);
         return prev;
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain IntVar but contains " + o);
      }
   }

   @Override
   public void unset(Session session) {
      ObjectVar var = getVarToSet(session);
      Object o = getItemFromVar(session, var);

      if (o instanceof IntVar) {
         if (trace) {
            log.trace("#{} unset {}[{}]", session.uniqueId(), key, session.currentSequence().index());
         }
         ((IntVar) o).unset();
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException(
               "Variable " + key + "[" + index + "] should contain Session.Var(Object) but contains " + o);
      }
   }
}

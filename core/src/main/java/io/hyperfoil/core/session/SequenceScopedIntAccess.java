package io.hyperfoil.core.session;

import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;

class SequenceScopedIntAccess extends SequenceScopedReadAccess implements IntAccess {
   SequenceScopedIntAccess(Object key, int maxConcurrency) {
      super(key, maxConcurrency);
   }

   @Override
   public void reserve(Session session) {
      SessionImpl impl = (SessionImpl) session;
      impl.reserveObjectVar(key);
      ObjectVar var = impl.getVar(key);
      if (!var.isSet() && var.objectValue(session) == null) {
         var.set(IntVar.newArray(session, maxConcurrency));
      }
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
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain Session.Var(Object) but contains " + o);
      }
   }
}

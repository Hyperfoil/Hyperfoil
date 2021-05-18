package io.hyperfoil.core.session;

import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;

class SequenceScopedObjectAccess extends SequenceScopedReadAccess implements ObjectAccess {
   SequenceScopedObjectAccess(Object key, int maxConcurrency) {
      super(key, maxConcurrency);
   }

   @Override
   public void reserve(Session session) {
      // When a step/action sets a variable, it doesn't know if that's a global or sequence-scoped
      // and must declare it, just in case.
      SessionImpl impl = (SessionImpl) session;
      impl.reserveObjectVar(key);
      ObjectVar var = impl.getVar(key);
      if (!var.isSet() && var.objectValue(session) == null) {
         var.set(ObjectVar.newArray(session, maxConcurrency));
      }
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
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain Session.Var(Object) but contains " + o);
      }
   }

}

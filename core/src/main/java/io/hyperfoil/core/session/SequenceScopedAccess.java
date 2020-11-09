package io.hyperfoil.core.session;

import java.lang.reflect.Array;
import java.util.List;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class SequenceScopedAccess implements Access {
   private static final Logger log = LoggerFactory.getLogger(SequenceScopedAccess.class);
   private static final boolean trace = log.isTraceEnabled();

   private final Object key;
   private final int maxConcurrency;

   SequenceScopedAccess(Object key, int maxConcurrency) {
      this.key = key;
      this.maxConcurrency = maxConcurrency;
   }

   @Override
   public void declareObject(Session session) {
      // When a step/action sets a variable, it doesn't know if that's a global or sequence-scoped
      // and must declare it, just in case.
      SessionImpl impl = (SessionImpl) session;
      impl.declareObject(key);
      ObjectVar var = impl.getVar(key);
      if (!var.isSet() && var.objectValue(session) == null) {
         var.set(ObjectVar.newArray(session, maxConcurrency));
      }
   }

   @Override
   public void declareInt(Session session) {
      SessionImpl impl = (SessionImpl) session;
      impl.declareObject(key);
      ObjectVar var = impl.getVar(key);
      if (!var.isSet() && var.objectValue(session) == null) {
         var.set(IntVar.newArray(session, maxConcurrency));
      }
   }

   @Override
   public boolean isSet(Session session) {
      SessionImpl impl = (SessionImpl) session;
      Session.Var var = impl.getVar(key);
      if (!var.isSet()) {
         return false;
      }

      Object o = getItemFromVar(session, var);
      if (o instanceof Session.Var) {
         return ((Session.Var) o).isSet();
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain Session.Var but contains " + o);
      }
   }

   @Override
   public Object getObject(Session session) {
      Object o = getItem(session);

      if (o instanceof ObjectVar) {
         ObjectVar ov = (ObjectVar) o;
         if (!ov.isSet()) {
            int index = session.currentSequence().index();
            throw new IllegalStateException("Variable " + key + "[" + index + "] was not set yet!");
         }
         return ov.objectValue(session);
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain ObjectVar but contains " + o);
      }
   }

   @Override
   public void setObject(Session session, Object value) {
      Session.Var var = getVarToSet(session);
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
   public int getInt(Session session) {
      Object o = getItem(session);

      if (o instanceof IntVar) {
         IntVar iv = (IntVar) o;
         if (!iv.isSet()) {
            int index = session.currentSequence().index();
            throw new IllegalStateException("Variable " + key + "[" + index + "] was not set yet!");
         }
         return iv.intValue(session);
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain IntVar but contains " + o);
      }
   }

   @Override
   public void setInt(Session session, int value) {
      Session.Var var = getVarToSet(session);
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

   private Session.Var getVarToSet(Session session) {
      SessionImpl impl = (SessionImpl) session;
      Session.Var var = impl.getVar(key);
      if (var instanceof ObjectVar) {
         ((ObjectVar) var).set = true;
      } else {
         throw new IllegalStateException("Variable " + key + " does not hold an object variable (cannot hold array).");
      }
      return var;
   }

   @Override
   public Session.Var getVar(Session session) {
      Object o = getItem(session);

      if (o instanceof Session.Var) {
         return (Session.Var) o;
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain Session.Var but contains " + o);
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
      Session.Var var = getVarToSet(session);
      Object o = getItemFromVar(session, var);

      if (o instanceof Session.Var) {
         if (trace) {
            log.trace("#{} unset {}[{}] <- {}", session.uniqueId(), key, session.currentSequence().index());
         }
         ((Session.Var) o).unset();
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain Session.Var but contains " + o);
      }
   }

   @Override
   public boolean isSequenceScoped() {
      return true;
   }

   @Override
   public String toString() {
      return key + "[.]";
   }

   private Object getItem(Session session) {
      SessionImpl impl = (SessionImpl) session;
      Session.Var var = impl.getVar(key);
      if (!var.isSet()) {
         throw new IllegalStateException("Variable " + key + " is not set!");
      }
      return getItemFromVar(session, var);
   }

   private Object getItemFromVar(Session session, Session.Var var) {
      Object collection = var.objectValue(session);
      if (collection == null) {
         throw new IllegalStateException("Variable " + key + " is null!");
      }
      int index = session.currentSequence().index();
      if (index >= maxConcurrency) {
         throw new IllegalStateException("Variable " + key + " reads item at index " + index + " but the maximum concurrency is " + maxConcurrency);
      }
      if (collection.getClass().isArray()) {
         return Array.get(collection, index);
      } else if (collection instanceof List) {
         return ((List<?>) collection).get(index);
      } else {
         throw new IllegalStateException("Unknown type to access by index: " + collection);
      }
   }
}

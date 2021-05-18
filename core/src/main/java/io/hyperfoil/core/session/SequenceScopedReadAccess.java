package io.hyperfoil.core.session;

import java.lang.reflect.Array;
import java.util.List;

import io.hyperfoil.api.session.Session;

class SequenceScopedReadAccess extends BaseAccess {
   protected final int maxConcurrency;

   public SequenceScopedReadAccess(Object key, int maxConcurrency) {
      super(key);
      this.maxConcurrency = maxConcurrency;
   }

   @Override
   public boolean isSet(Session session) {
      SessionImpl impl = (SessionImpl) session;
      ObjectVar var = impl.getVar(index);
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

      if (o instanceof Session.Var) {
         Session.Var ov = (Session.Var) o;
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
   public boolean isSequenceScoped() {
      return true;
   }

   @Override
   public String toString() {
      return key + "[.]";
   }

   protected Object getItem(Session session) {
      SessionImpl impl = (SessionImpl) session;
      ObjectVar var = impl.getVar(index);
      if (!var.isSet()) {
         throw new IllegalStateException("Variable " + key + " is not set!");
      }
      return getItemFromVar(session, var);
   }

   protected Object getItemFromVar(Session session, ObjectVar var) {
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

   protected ObjectVar getVarToSet(Session session) {
      SessionImpl impl = (SessionImpl) session;
      Session.Var var = impl.getVar(index);
      if (var instanceof ObjectVar) {
         ObjectVar ov = (ObjectVar) var;
         ov.set = true;
         return ov;
      } else {
         throw new IllegalStateException("Variable " + key + " does not hold an object variable (cannot hold array).");
      }
   }
}

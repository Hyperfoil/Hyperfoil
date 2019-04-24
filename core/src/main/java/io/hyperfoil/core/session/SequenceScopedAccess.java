package io.hyperfoil.core.session;

import java.lang.reflect.Array;
import java.util.List;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;

class SequenceScopedAccess implements Access {
   private final String key;

   SequenceScopedAccess(String key) {
      this.key = key;
   }

   @Override
   public void declareObject(Session session) {
      // When a step/action sets a variable, it doesn't know if that's a global or sequence-scoped
      // and must declare it, just in case.
   }

   @Override
   public void declareInt(Session session) {
   }

   @Override
   public boolean isSet(Session session) {
      Object result;
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
         return ov.objectValue();
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain ObjectVar but contains " + o);
      }
   }

   @Override
   public void setObject(Session session, Object value) {
      Object o = getItem(session);

      if (o instanceof ObjectVar) {
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
         return iv.intValue();
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain IntVar but contains " + o);
      }
   }

   @Override
   public void setInt(Session session, int value) {
      Object o = getItem(session);

      if (o instanceof IntVar) {
         ((IntVar) o).set(value);
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain IntVar but contains " + o);
      }
   }

   @SuppressWarnings("unchecked")
   @Override
   public <V extends Session.Var> V getVar(Session session) {
      Object o = getItem(session);

      if (o instanceof Session.Var) {
         return (V) o;
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
         int prev = iv.intValue();
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
         ov.set = true;
         return ov.objectValue();
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain ObjectVar but contains " + o);
      }
   }

   @Override
   public void unset(Session session) {
      Object o = getItem(session);

      if (o instanceof Session.Var) {
         ((Session.Var) o).unset();
      } else {
         int index = session.currentSequence().index();
         throw new IllegalStateException("Variable " + key + "[" + index + "] should contain Session.Var but contains " + o);
      }
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
      Object collection = var.objectValue();
      if (collection == null) {
         throw new IllegalStateException("Variable " + key + " is null!");
      }
      int index = session.currentSequence().index();
      if (collection.getClass().isArray()) {
         return Array.get(collection, index);
      } else if (collection instanceof List) {
         return ((List) collection).get(index);
      } else {
         throw new IllegalStateException("Unknown type to access by index: " + collection);
      }
   }
}

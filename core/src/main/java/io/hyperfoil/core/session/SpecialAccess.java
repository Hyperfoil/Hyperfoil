package io.hyperfoil.core.session;

import java.io.Serializable;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableToIntFunction;

abstract class SpecialAccess implements Access {
   final String name;

   SpecialAccess(String name) {
      this.name = name;
   }

   static class Int extends SpecialAccess {
      private final SerializableToIntFunction<Session> supplier;
      private final Var var = new Var();

      public Int(String name, SerializableToIntFunction<Session> supplier) {
         super(name);
         this.supplier = supplier;
      }

      @Override
      public void declareObject(Session session) {
         // noop
      }

      @Override
      public void declareInt(Session session) {
         // noop
      }

      @Override
      public boolean isSet(Session session) {
         return true;
      }

      @Override
      public Object getObject(Session session) {
         throw new UnsupportedOperationException("Cannot retrieve " + name + " as object");
      }

      @Override
      public void setObject(Session session, Object value) {
         throw new UnsupportedOperationException(name + " is read-only");
      }

      @Override
      public int getInt(Session session) {
         return supplier.applyAsInt(session);
      }

      @Override
      public void setInt(Session session, int value) {
         throw new UnsupportedOperationException(name + " is read-only");
      }

      @Override
      public Session.Var getVar(Session session) {
         return var;
      }

      @Override
      public int addToInt(Session session, int delta) {
         throw new UnsupportedOperationException(name + " is read-only");
      }

      @Override
      public Object activate(Session session) {
         throw new UnsupportedOperationException("Cannot retrieve " + name + " as an object");
      }

      @Override
      public void unset(Session session) {
         throw new UnsupportedOperationException(name + " is read-only");
      }

      private class Var implements Session.Var, Serializable {
         @Override
         public boolean isSet() {
            return true;
         }

         @Override
         public void unset() {
            throw new UnsupportedOperationException("Cannot unset " + name + "; it is read-only.");
         }

         @Override
         public Session.VarType type() {
            return Session.VarType.INTEGER;
         }

         @Override
         public int intValue(Session session) {
            return supplier.applyAsInt(session);
         }

         @Override
         public Object objectValue(Session session) {
            throw new UnsupportedOperationException();
         }
      }
   }
}

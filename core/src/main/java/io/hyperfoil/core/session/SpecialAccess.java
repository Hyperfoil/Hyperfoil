package io.hyperfoil.core.session;

import java.io.Serializable;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.function.SerializableToIntFunction;

abstract class SpecialAccess implements Access {
   final String name;

   SpecialAccess(String name) {
      this.name = name;
   }

   @Override
   public boolean isSequenceScoped() {
      return false;
   }

   @Override
   public void setInt(Session session, int value) {
      throw new UnsupportedOperationException(name + " is read-only");
   }

   @Override
   public int addToInt(Session session, int delta) {
      throw new UnsupportedOperationException(name + " is read-only");
   }

   @Override
   public void unset(Session session) {
      throw new UnsupportedOperationException(name + " is read-only");
   }

   @Override
   public void declareObject(Session session) {
      throw new UnsupportedOperationException("Cannot declare read-only variable " + name);
   }

   @Override
   public void declareInt(Session session) {
      throw new UnsupportedOperationException("Cannot declare read-only variable " + name);
   }

   @Override
   public boolean isSet(Session session) {
      return true;
   }

   @Override
   public void setObject(Session session, java.lang.Object value) {
      throw new UnsupportedOperationException(name + " is read-only");
   }

   private abstract class BaseVar implements Session.Var, Serializable {
      @Override
      public boolean isSet() {
         return true;
      }

      @Override
      public void unset() {
         throw new UnsupportedOperationException("Cannot unset " + name + "; it is read-only.");
      }
   }

   static class Int extends SpecialAccess {
      private final SerializableToIntFunction<Session> supplier;
      private final Var var = new Var();

      public Int(String name, SerializableToIntFunction<Session> supplier) {
         super(name);
         this.supplier = supplier;
      }

      @Override
      public Object getObject(Session session) {
         throw new UnsupportedOperationException("Cannot retrieve " + name + " as object");
      }


      @Override
      public int getInt(Session session) {
         return supplier.applyAsInt(session);
      }

      @Override
      public Session.Var getVar(Session session) {
         return var;
      }

      @Override
      public Object activate(Session session) {
         throw new UnsupportedOperationException("Cannot retrieve " + name + " as an object");
      }

      private class Var extends BaseVar {
         @Override
         public Session.VarType type() {
            return Session.VarType.INTEGER;
         }

         @Override
         public int intValue(Session session) {
            return supplier.applyAsInt(session);
         }
      }
   }

   static class Object extends SpecialAccess {
      private final SerializableFunction<Session, java.lang.Object> supplier;
      private final Var var = new Var();

      Object(String name, SerializableFunction<Session, java.lang.Object> supplier) {
         super(name);
         this.supplier = supplier;
      }

      @Override
      public boolean isSet(Session session) {
         return true;
      }

      @Override
      public java.lang.Object getObject(Session session) {
         return supplier.apply(session);
      }

      @Override
      public int getInt(Session session) {
         throw new UnsupportedOperationException("Cannot retrieve " + name + " as integer");
      }

      @Override
      public Session.Var getVar(Session session) {
         return var;
      }

      @Override
      public java.lang.Object activate(Session session) {
         return supplier.apply(session);
      }

      private class Var extends BaseVar {
         @Override
         public Session.VarType type() {
            return Session.VarType.OBJECT;
         }

         public java.lang.Object objectValue(Session session) {
            return supplier.apply(session);
         }
      }
   }
}

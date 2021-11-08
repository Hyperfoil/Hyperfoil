package io.hyperfoil.core.session;

import java.io.Serializable;
import java.util.Objects;

import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.function.SerializableToIntFunction;

abstract class SpecialAccess implements ReadAccess {
   final String name;

   SpecialAccess(String name) {
      this.name = Objects.requireNonNull(name);
   }

   @Override
   public boolean isSequenceScoped() {
      return false;
   }

   @Override
   public java.lang.Object key() {
      // we return null instead of `name` to avoid the read-without-write check
      return null;
   }

   @Override
   public void setIndex(int index) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int index() {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean isSet(Session session) {
      return true;
   }

   @Override
   public boolean equals(java.lang.Object obj) {
      if (obj instanceof ReadAccess) {
         return name.equals(((ReadAccess) obj).key());
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(name);
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

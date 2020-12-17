package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Util;

public class BoolCondition implements Condition {
   private final Access fromVar;
   private final boolean value;

   public BoolCondition(Access fromVar, boolean value) {
      this.fromVar = fromVar;
      this.value = value;
   }

   @Override
   public boolean test(Session session) {
      Session.Var var = fromVar.getVar(session);
      if (!var.isSet()) {
         return false;
      }
      if (var.type() == Session.VarType.INTEGER) {
         // this is not C - we won't convert 1 to true, 0 to false
         return false;
      } else if (var.type() == Session.VarType.OBJECT) {
         Object obj = var.objectValue(session);
         if (obj instanceof Boolean) {
            return (Boolean) obj == value;
         } else if (obj instanceof CharSequence) {
            CharSequence str = (CharSequence) obj;
            if (value) {
               return Util.regionMatchesIgnoreCase(str, 0, "true", 0, 4);
            } else {
               return Util.regionMatchesIgnoreCase(str, 0, "false", 0, 5);
            }
         }
         return false;
      } else {
         throw new IllegalStateException("Unknown type of var: " + var);
      }
   }

   /**
    * Tests session variable containing boolean value.
    */
   public static class Builder<P> implements Condition.Builder<Builder<P>> {
      private final P parent;
      private String fromVar;
      private Boolean value;

      public Builder(P parent) {
         this.parent = parent;
      }

      @Override
      public BoolCondition buildCondition() {
         return new BoolCondition(SessionFactory.access(fromVar), value);
      }

      /**
       * Variable name.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder<P> fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      /**
       * Expected value.
       *
       * @param value True or false.
       * @return Self.
       */
      public Builder<P> value(boolean value) {
         this.value = value;
         return this;
      }

      public P end() {
         return parent;
      }
   }
}

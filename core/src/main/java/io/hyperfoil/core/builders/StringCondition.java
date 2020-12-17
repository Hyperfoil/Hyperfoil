package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableBiPredicate;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class StringCondition implements Condition {
   private static final Logger log = LoggerFactory.getLogger(StringCondition.class);
   private static final boolean trace = log.isTraceEnabled();

   private final Access fromVar;
   private final boolean isSet;
   private final SerializableBiPredicate<Session, CharSequence> predicate;

   public StringCondition(Access fromVar, boolean isSet, SerializableBiPredicate<Session, CharSequence> predicate) {
      this.fromVar = fromVar;
      this.isSet = isSet;
      this.predicate = predicate;
   }

   @Override
   public boolean test(Session session) {
      Session.Var var = fromVar.getVar(session);
      if (!var.isSet()) {
         if (trace) {
            log.trace("#{} Variable {} is not set, condition result: {}", session.uniqueId(), fromVar, !isSet);
         }
         return !isSet;
      } else if (!isSet) {
         if (trace) {
            log.trace("#{} Variable {} is set, condition result: false", session.uniqueId(), fromVar);
         }
         return false;
      } else if (predicate == null) {
         if (trace) {
            log.trace("#{} No predicate on variable {}, condition result: true", session.uniqueId(), fromVar);
         }
         return true;
      }
      CharSequence value;
      if (var.type() == Session.VarType.INTEGER) {
         value = String.valueOf(var.intValue(session));
      } else if (var.type() == Session.VarType.OBJECT) {
         Object obj = var.objectValue(session);
         if (!(obj instanceof CharSequence)) {
            return false;
         }
         value = (CharSequence) obj;
      } else {
         throw new IllegalStateException("Unknown type of var: " + var);
      }
      boolean result = predicate.test(session, value);
      if (trace) {
         log.trace("#{} Variable {} = {}, condition result: {}", session.uniqueId(), fromVar, value, result);
      }
      return result;
   }

   /**
    * Condition comparing string in session variable.
    */
   public static class Builder<P> extends StringConditionBuilder<Builder<P>, P> implements Condition.Builder<Builder<P>> {
      private Object fromVar;
      private boolean isSet = true;

      public Builder(P parent) {
         super(parent);
      }

      /**
       * Variable name.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder<P> fromVar(Object var) {
         this.fromVar = var;
         return this;
      }

      /**
       * Check if the value is set or unset. By default the variable must be set.
       *
       * @param isSet True or false.
       * @return Self.
       */
      public Builder<P> isSet(boolean isSet) {
         this.isSet = isSet;
         return this;
      }

      public StringCondition buildCondition() {
         return new StringCondition(SessionFactory.access(fromVar), isSet, isSet ? buildPredicate() : null);
      }
   }
}

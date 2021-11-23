package io.hyperfoil.core.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableBiPredicate;

public class StringCondition extends StringConditionBase implements Condition {
   private static final Logger log = LogManager.getLogger(StringCondition.class);
   private static final boolean trace = log.isTraceEnabled();

   private final ReadAccess fromVar;
   private final boolean isSet;

   public StringCondition(ReadAccess fromVar, boolean isSet, SerializableBiPredicate<Session, CharSequence> predicate) {
      super(predicate);
      this.fromVar = fromVar;
      this.isSet = isSet;
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
      }
      return testVar(session, var);
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
         return new StringCondition(SessionFactory.readAccess(fromVar), isSet, isSet ? buildPredicate() : null);
      }
   }
}

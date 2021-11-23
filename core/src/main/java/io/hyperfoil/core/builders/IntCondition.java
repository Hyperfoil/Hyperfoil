package io.hyperfoil.core.builders;

import java.io.Serializable;
import java.util.function.Supplier;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class IntCondition extends IntConditionBase implements Condition {
   private final ReadAccess fromVar;
   private final boolean isSet;

   public IntCondition(ReadAccess fromVar, boolean isSet, Predicate predicate) {
      super(predicate);
      this.fromVar = fromVar;
      this.isSet = isSet;
   }

   @Override
   public boolean test(Session session) {
      Session.Var var = fromVar.getVar(session);
      if (!var.isSet()) {
         return !isSet;
      } else if (!isSet) {
         return false;
      }
      return testVar(session, var);
   }

   /**
    * Condition comparing integer in session variable.
    */
   public static class Builder<P> extends IntConditionBuilder<Builder<P>, P> implements Condition.Builder<Builder<P>>, InitFromParam<Builder<P>> {
      private Object fromVar;
      private boolean isSet = true;

      public Builder() {
         this(null);
      }

      public Builder(P parent) {
         super(parent);
      }

      @Override
      public Builder<P> init(String param) {
         if (tryOp(param, "==", this::equalTo) ||
               tryOp(param, "!=", this::notEqualTo) ||
               tryOp(param, "<>", this::notEqualTo) ||
               tryOp(param, ">=", this::greaterOrEqualTo) ||
               tryOp(param, ">", this::greaterThan) ||
               tryOp(param, "<=", this::lessOrEqualTo) ||
               tryOp(param, "<", this::lessThan)) {
            return this;
         } else {
            throw new BenchmarkDefinitionException("Cannot parse intCondition: " + param);
         }
      }

      private boolean tryOp(String param, String operator, Supplier<IntSourceBuilder<Builder<P>>> mutator) {
         if (param.contains(operator)) {
            String[] parts = param.split(operator);
            if (parts.length == 2) {
               try {
                  int value = Integer.parseInt(parts[1].trim());
                  fromVar(parts[0].trim());
                  mutator.get().value(value);
                  return true;
               } catch (NumberFormatException e) {
                  throw new BenchmarkDefinitionException("Cannot parse intCondition: " + param);
               }
            }
         }
         return false;
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

      @Override
      public IntCondition buildCondition() {
         return new IntCondition(SessionFactory.readAccess(fromVar), isSet, buildPredicate());
      }
   }

   public static class ProvidedVarBuilder<P> extends IntConditionBuilder<ProvidedVarBuilder<P>, P> {
      public ProvidedVarBuilder(P parent) {
         super(parent);
      }

      public IntCondition build(String var) {
         return new IntCondition(SessionFactory.readAccess(var), true, buildPredicate());
      }
   }

   public interface Predicate extends Serializable {
      boolean test(Session session, int value);
   }
}

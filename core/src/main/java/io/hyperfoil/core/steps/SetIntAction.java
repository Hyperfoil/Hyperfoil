package io.hyperfoil.core.steps;

import java.util.Objects;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.IntCondition;
import io.hyperfoil.core.builders.IntSourceBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableToIntFunction;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SetIntAction implements Action {
   private static final Logger log = LogManager.getLogger(SetIntAction.class);
   private static final boolean trace = log.isTraceEnabled();

   private final IntAccess var;
   private final SerializableToIntFunction<Session> input;
   private final boolean onlyIfNotSet;
   private final IntCondition condition;

   public SetIntAction(IntAccess var, SerializableToIntFunction<Session> input, boolean onlyIfNotSet, IntCondition condition) {
      this.var = var;
      this.input = input;
      this.onlyIfNotSet = onlyIfNotSet;
      this.condition = condition;
   }

   @Override
   public void run(Session session) {
      if (onlyIfNotSet && var.isSet(session)) {
         if (trace) {
            int value = input.applyAsInt(session);
            log.trace("#{} Not setting {} to {} as it is already set to {}", session.uniqueId(), var, value, var.getInt(session));
         }
         return;
      }
      if (condition == null || condition.test(session)) {
         var.setInt(session, input.applyAsInt(session));
      }
   }

   /**
    * Set session variable to an integral value.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("setInt")
   public static class Builder implements InitFromParam<Builder>, Action.Builder {
      private String var;
      private Integer value;
      private String fromVar;
      private IntSourceBuilder.ListBuilder min, max;
      private boolean onlyIfNotSet;
      private IntCondition.ProvidedVarBuilder<Builder> intCondition;

      public Builder() {
      }

      /**
       * @param param Use <code>var &lt;- value</code>.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         int sep = param.indexOf("<-");
         if (sep < 0) {
            throw new BenchmarkDefinitionException("Invalid inline definition '" + param + "': should be 'var <- value'");
         }
         this.var = param.substring(0, sep).trim();
         try {
            this.value = Integer.parseInt(param.substring(sep + 2).trim());
         } catch (NumberFormatException e) {
            throw new BenchmarkDefinitionException("Cannot parse value as int: " + param.substring(sep + 2), e);
         }
         return this;
      }

      /**
       * Variable name.
       *
       * @param var Output variable name.
       * @return Self.
       */
      public Builder var(String var) {
         this.var = var;
         return this;
      }

      /**
       * Value (integer).
       *
       * @param value Value.
       * @return Self.
       */
      public Builder value(int value) {
         this.value = value;
         return this;
      }

      /**
       * Input variable name.
       *
       * @param fromVar Input variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      /**
       * Set to value that is the minimum of this list of values.
       *
       * @return Builder.
       */
      public IntSourceBuilder.ListBuilder min() {
         return min = new IntSourceBuilder.ListBuilder();
      }

      /**
       * Set to value that is the maximum of this list of values.
       *
       * @return Builder.
       */
      public IntSourceBuilder.ListBuilder max() {
         return max = new IntSourceBuilder.ListBuilder();
      }


      /**
       * Set variable to the value only if it is not already set.
       *
       * @param onlyIfNotSet If false (default) the value is always set.
       * @return Self.
       */
      public Builder onlyIfNotSet(boolean onlyIfNotSet) {
         this.onlyIfNotSet = onlyIfNotSet;
         return this;
      }

      /**
       * Set variable only if the current value satisfies certain condition.
       *
       * @return Builder.
       */
      public IntCondition.ProvidedVarBuilder<Builder> intCondition() {
         return intCondition = new IntCondition.ProvidedVarBuilder<>(this);
      }

      @Override
      public SetIntAction build() {
         if (var == null) {
            throw new BenchmarkDefinitionException("No variable set!");
         }
         if (Stream.of(value, fromVar, min, max).filter(Objects::nonNull).count() != 1) {
            throw new BenchmarkDefinitionException("Must set exactly one of these properties: 'value', 'fromVar', 'min', 'max'");
         }
         SerializableToIntFunction<Session> input;
         if (value != null) {
            input = session -> value;
         } else if (fromVar != null) {
            input = SessionFactory.readAccess(fromVar)::getInt;
         } else if (min != null) {
            SerializableToIntFunction<Session>[] items = min.build();
            if (items.length == 0) {
               throw new BenchmarkDefinitionException("Must calculate minimum from at least one item!");
            }
            input = session -> {
               int min = items[0].applyAsInt(session);
               for (int i = 1; i < items.length; ++i) {
                  min = Math.min(min, items[i].applyAsInt(session));
               }
               return min;
            };
         } else if (max != null) {
            SerializableToIntFunction<Session>[] items = max.build();
            if (items.length == 0) {
               throw new BenchmarkDefinitionException("Must calculate maximum from at least one item!");
            }
            input = session -> {
               int max = items[0].applyAsInt(session);
               for (int i = 1; i < items.length; ++i) {
                  max = Math.max(max, items[i].applyAsInt(session));
               }
               return max;
            };
         } else {
            throw new IllegalStateException();
         }
         return new SetIntAction(SessionFactory.intAccess(var), input, onlyIfNotSet,
               intCondition == null ? null : intCondition.build(var));
      }
   }
}

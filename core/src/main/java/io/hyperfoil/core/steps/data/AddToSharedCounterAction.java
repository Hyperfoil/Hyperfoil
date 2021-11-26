package io.hyperfoil.core.steps.data;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ThreadData;
import io.hyperfoil.core.builders.IntSourceBuilder;
import io.hyperfoil.function.SerializableLongBinaryOperator;
import io.hyperfoil.function.SerializableToIntFunction;

public class AddToSharedCounterAction implements Action, ResourceUtilizer {
   private final String key;
   private final SerializableToIntFunction<Session> input;
   private final SerializableLongBinaryOperator operator;

   public AddToSharedCounterAction(String key, SerializableToIntFunction<Session> input, SerializableLongBinaryOperator operator) {
      this.key = key;
      this.input = input;
      this.operator = operator;
   }

   @Override
   public void run(Session session) {
      ThreadData.SharedCounter counter = session.threadData().getCounter(key);
      counter.apply(operator, input.applyAsInt(session));
   }

   @Override
   public void reserve(Session session) {
      session.threadData().reserveCounter(key);
   }

   /**
    * Adds value to a counter shared by all sessions in the same executor.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("addToSharedCounter")
   public static class Builder implements Action.Builder, InitFromParam<Builder> {
      private String key;
      private Operator operator = Operator.ADD;
      @Embed
      public IntSourceBuilder<Builder> input = new IntSourceBuilder<>(this);

      /**
       * @param param Use on of: <code>counter++</code>, <code>counter--</code>, <code>counter += &lt;value&gt;</code>,
       *              <code>counter -= &lt;value&gt;</code>
       * @return
       */
      @Override
      public Builder init(String param) {
         param = param.trim();
         if (param.endsWith("++")) {
            key = param.substring(0, param.length() - 2).trim();
            input.value(1);
         } else if (param.endsWith("--")) {
            key = param.substring(0, param.length() - 2).trim();
            input.value(-1);
         } else if (param.contains("+=")) {
            int plusEqualsIndex = param.indexOf("+=");
            key = param.substring(0, plusEqualsIndex).trim();
            input.value(Integer.parseInt(param.substring(plusEqualsIndex + 2).trim()));
         } else if (param.contains("-=")) {
            int minusEqualsIndex = param.indexOf("-=");
            key = param.substring(0, minusEqualsIndex).trim();
            input.value(-Integer.parseInt(param.substring(minusEqualsIndex + 2).trim()));
         } else {
            throw new BenchmarkDefinitionException("Accepting one of: var++, var--, var += value, var -= value");
         }
         return this;
      }

      /**
       * Identifier for the counter.
       *
       * @param key Name.
       * @return Self.
       */
      public Builder key(String key) {
         this.key = key;
         return this;
      }

      /**
       * Operation to perform on the counter. Default is <code>ADD</code>.
       *
       * @param operator The operator.
       * @return Self.
       */
      public Builder operator(Operator operator) {
         this.operator = operator;
         return this;
      }

      @Override
      public AddToSharedCounterAction build() {
         if (key == null || key.isEmpty()) {
            throw new BenchmarkDefinitionException("Invalid key: " + key);
         }
         return new AddToSharedCounterAction(key, input.build(), operator.operator);
      }
   }

   public enum Operator {
      ADD(Long::sum),
      SUBTRACT((a, b) -> a - b);
      final SerializableLongBinaryOperator operator;

      Operator(SerializableLongBinaryOperator operator) {
         this.operator = operator;
      }
   }
}

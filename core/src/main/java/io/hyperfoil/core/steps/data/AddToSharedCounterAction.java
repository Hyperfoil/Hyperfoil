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
import io.hyperfoil.function.SerializableToIntFunction;

public class AddToSharedCounterAction implements Action, ResourceUtilizer {
   private final String key;
   private final SerializableToIntFunction<Session> input;

   public AddToSharedCounterAction(String key, SerializableToIntFunction<Session> input) {
      this.key = key;
      this.input = input;
   }

   @Override
   public void run(Session session) {
      ThreadData.SharedCounter counter = session.threadData().getCounter(key);
      counter.add(input.applyAsInt(session));
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
      @Embed
      public IntSourceBuilder<Builder> input = new IntSourceBuilder<>(this);

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

      @Override
      public AddToSharedCounterAction build() {
         return new AddToSharedCounterAction(key, input.build());
      }
   }
}

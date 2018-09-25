package io.sailrocket.core.parser;

import java.util.function.BiConsumer;

import org.yaml.snakeyaml.events.ScalarEvent;

import io.sailrocket.core.builders.ScenarioBuilder;

public class VarParser implements Parser<ScenarioBuilder> {
   private final BiConsumer<ScenarioBuilder, String> consumer;

   public VarParser(BiConsumer<ScenarioBuilder, String> consumer) {
      this.consumer = consumer;
   }

   @Override
   public void parse(Context ctx, ScenarioBuilder target) throws ConfigurationParserException {
      ctx.parseList(target, this::parseVar);
   }

   private void parseVar(Context ctx, ScenarioBuilder target) throws ConfigurationParserException {
      ScalarEvent event = ctx.expectEvent(ScalarEvent.class);
      consumer.accept(target, event.getValue());
   }
}

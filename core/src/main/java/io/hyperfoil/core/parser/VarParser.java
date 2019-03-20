package io.hyperfoil.core.parser;

import java.util.function.BiConsumer;

import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.hyperfoil.api.config.ScenarioBuilder;

class VarParser implements Parser<ScenarioBuilder> {
   private final BiConsumer<ScenarioBuilder, String> consumer;

   VarParser(BiConsumer<ScenarioBuilder, String> consumer) {
      this.consumer = consumer;
   }

   @Override
   public void parse(Context ctx, ScenarioBuilder target) throws ParserException {
      ctx.parseList(target, this::parseVar);
   }

   private void parseVar(Context ctx, ScenarioBuilder target) throws ParserException {
      ctx.expectEvent(MappingStartEvent.class);
      ScalarEvent event = ctx.expectEvent(ScalarEvent.class);
      consumer.accept(target, event.getValue());
      ctx.expectEvent(MappingEndEvent.class);
   }
}

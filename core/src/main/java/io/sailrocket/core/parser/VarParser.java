package io.sailrocket.core.parser;

import java.util.Iterator;
import java.util.function.BiConsumer;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.sailrocket.core.builders.ScenarioBuilder;

public class VarParser extends BaseParser<ScenarioBuilder> {
   private final BiConsumer<ScenarioBuilder, String> consumer;

   public VarParser(BiConsumer<ScenarioBuilder, String> consumer) {
      this.consumer = consumer;
   }

   @Override
   public void parse(Iterator<Event> events, ScenarioBuilder target) throws ConfigurationParserException {
      parseList(events, target, this::parseVar);
   }

   private void parseVar(Iterator<Event> events, ScenarioBuilder target) throws ConfigurationParserException {
      ScalarEvent event = expectEvent(events, ScalarEvent.class);
      consumer.accept(target, event.getValue());
   }
}

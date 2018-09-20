package io.sailrocket.core.parser;

import java.util.Iterator;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;

import io.sailrocket.core.builders.HttpBuilder;
import io.sailrocket.core.builders.SimulationBuilder;

public class HttpParser extends AbstractParser<SimulationBuilder, HttpBuilder> {
   HttpParser() {
      subBuilders.put("baseUrl", new PropertyParser.String<>(HttpBuilder::baseUrl));
   }

   @Override
   public void parse(Iterator<Event> events, SimulationBuilder target) throws ConfigurationParserException {
      callSubBuilders(events, target.http(), MappingEndEvent.class);
   }
}

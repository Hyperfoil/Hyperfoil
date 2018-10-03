package io.sailrocket.core.parser;

import io.sailrocket.core.builders.HttpBuilder;
import io.sailrocket.core.builders.SimulationBuilder;

public class HttpParser extends AbstractParser<SimulationBuilder, HttpBuilder> {
   HttpParser() {
      subBuilders.put("baseUrl", new PropertyParser.String<>(HttpBuilder::baseUrl));
   }

   @Override
   public void parse(Context ctx, SimulationBuilder target) throws ConfigurationParserException {
      callSubBuilders(ctx, target.http());
   }
}

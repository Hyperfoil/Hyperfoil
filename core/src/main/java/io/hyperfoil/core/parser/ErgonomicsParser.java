package io.hyperfoil.core.parser;

import io.hyperfoil.api.config.ErgonomicsBuilder;
import io.hyperfoil.api.config.SimulationBuilder;

class ErgonomicsParser extends AbstractParser<SimulationBuilder, ErgonomicsBuilder> {
   ErgonomicsParser() {
      register("repeatCookies", new PropertyParser.Boolean<>(ErgonomicsBuilder::repeatCookies));
   }

   @Override
   public void parse(Context ctx, SimulationBuilder target) throws ParserException {
      callSubBuilders(ctx, target.ergonomics());
   }
}

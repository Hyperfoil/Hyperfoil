package io.hyperfoil.core.parser;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.ErgonomicsBuilder;

class ErgonomicsParser extends AbstractParser<BenchmarkBuilder, ErgonomicsBuilder> {
   ErgonomicsParser() {
      register("repeatCookies", new PropertyParser.Boolean<>(ErgonomicsBuilder::repeatCookies));
      register("privateHttpPools", new PropertyParser.Boolean<>(ErgonomicsBuilder::privateHttpPools));
      register("userAgentFromSession", new PropertyParser.Boolean<>(ErgonomicsBuilder::userAgentFromSession));
      register("autoRangeCheck", new PropertyParser.Boolean<>(ErgonomicsBuilder::autoRangeCheck));
      register("stopOnInvalid", new PropertyParser.Boolean<>(ErgonomicsBuilder::stopOnInvalid));
   }

   @Override
   public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
      callSubBuilders(ctx, target.ergonomics());
   }
}

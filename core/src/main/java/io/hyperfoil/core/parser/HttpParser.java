package io.hyperfoil.core.parser;

import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.core.builders.HttpBuilder;
import io.hyperfoil.core.builders.SimulationBuilder;

class HttpParser extends AbstractParser<SimulationBuilder, HttpBuilder> {
   HttpParser() {
      register("baseUrl", new PropertyParser.String<>(HttpBuilder::baseUrl));
      register("repeatCookies", new PropertyParser.Boolean<>(HttpBuilder::repeatCookies));
      register("allowHttp1x", new PropertyParser.Boolean<>(HttpBuilder::allowHttp1x));
      register("allowHttp2", new PropertyParser.Boolean<>(HttpBuilder::allowHttp2));
      register("maxHttp2Streams", new PropertyParser.Int<>(HttpBuilder::maxHttp2Streams));
      register("sharedConnections", new PropertyParser.Int<>(HttpBuilder::sharedConnections));
      register("pipeliningLimit", new PropertyParser.Int<>(HttpBuilder::pipeliningLimit));
      register("directHttp2", new PropertyParser.Boolean<>(HttpBuilder::directHttp2));
      register("requestTimeout", new PropertyParser.String<>(HttpBuilder::requestTimeout));
   }

   @Override
   public void parse(Context ctx, SimulationBuilder target) throws ParserException {
      if (ctx.peek() instanceof SequenceStartEvent) {
         ctx.parseList(target.decoupledHttp(), (ctx1, builder) -> {
            callSubBuilders(ctx1, builder);
            target.addHttp(builder);
         });
      } else {
         callSubBuilders(ctx, target.http());
      }
   }
}

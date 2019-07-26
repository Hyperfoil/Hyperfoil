package io.hyperfoil.core.parser;

import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.HttpBuilder;
import io.hyperfoil.api.config.Protocol;

class HttpParser extends AbstractParser<BenchmarkBuilder, HttpBuilder> {
   private static AddressParser ADDRESS_PARSER = new AddressParser();

   HttpParser() {
      register("protocol", new PropertyParser.String<>((builder, scheme) -> builder.protocol(Protocol.fromScheme(scheme))));
      register("host", new PropertyParser.String<>(HttpBuilder::host));
      register("port", new PropertyParser.Int<>(HttpBuilder::port));
      register("allowHttp1x", new PropertyParser.Boolean<>(HttpBuilder::allowHttp1x));
      register("allowHttp2", new PropertyParser.Boolean<>(HttpBuilder::allowHttp2));
      register("maxHttp2Streams", new PropertyParser.Int<>(HttpBuilder::maxHttp2Streams));
      register("sharedConnections", new PropertyParser.Int<>(HttpBuilder::sharedConnections));
      register("pipeliningLimit", new PropertyParser.Int<>(HttpBuilder::pipeliningLimit));
      register("directHttp2", new PropertyParser.Boolean<>(HttpBuilder::directHttp2));
      register("requestTimeout", new PropertyParser.String<>(HttpBuilder::requestTimeout));
      register("addresses", (ctx, builder) -> ctx.parseList(builder, ADDRESS_PARSER));
      register("rawBytesHandlers", new PropertyParser.Boolean<>(HttpBuilder::rawBytesHandlers));
   }

   @Override
   public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
      if (ctx.peek() instanceof SequenceStartEvent) {
         ctx.parseList(target.decoupledHttp(), (ctx1, builder) -> {
            callSubBuilders(ctx1, builder);
            target.addHttp(builder);
         });
      } else {
         callSubBuilders(ctx, target.http());
      }
   }

   private static class AddressParser implements Parser<HttpBuilder> {
      @Override
      public void parse(Context ctx, HttpBuilder target) throws ParserException {
         ScalarEvent event = ctx.expectEvent(ScalarEvent.class);
         target.addAddress(event.getValue());
      }
   }
}

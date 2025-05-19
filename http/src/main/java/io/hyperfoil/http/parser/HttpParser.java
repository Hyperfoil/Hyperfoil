package io.hyperfoil.http.parser;

import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.core.parser.AbstractParser;
import io.hyperfoil.core.parser.Context;
import io.hyperfoil.core.parser.Parser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.parser.PropertyParser;
import io.hyperfoil.core.parser.ReflectionParser;
import io.hyperfoil.http.config.ConnectionStrategy;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.config.Protocol;

public class HttpParser extends AbstractParser<BenchmarkBuilder, HttpBuilder> {
   private static final AddressParser ADDRESS_PARSER = new AddressParser();

   public HttpParser() {
      register("name", new PropertyParser.String<>(HttpBuilder::name));
      register("protocol", new PropertyParser.String<>((builder, scheme) -> builder.protocol(Protocol.fromScheme(scheme))));
      register("host", new PropertyParser.String<>(HttpBuilder::host));
      register("port", new PropertyParser.Int<>(HttpBuilder::port));
      register("allowHttp1x", new PropertyParser.Boolean<>(HttpBuilder::allowHttp1x));
      register("allowHttp2", new PropertyParser.Boolean<>(HttpBuilder::allowHttp2));
      register("maxHttp2Streams", new PropertyParser.Int<>(HttpBuilder::maxHttp2Streams));
      register("sharedConnections", new ConnectionPoolConfigParser());
      register("pipeliningLimit", new PropertyParser.Int<>(HttpBuilder::pipeliningLimit));
      register("directHttp2", new PropertyParser.Boolean<>(HttpBuilder::directHttp2));
      register("requestTimeout", new PropertyParser.String<>(HttpBuilder::requestTimeout));
      register("sslHandshakeTimeout", new PropertyParser.String<>(HttpBuilder::sslHandshakeTimeout));
      register("addresses", HttpParser::parseAddresses);
      register("rawBytesHandlers", new PropertyParser.Boolean<>(HttpBuilder::rawBytesHandlers));
      register("keyManager", new ReflectionParser<>(HttpBuilder::keyManager));
      register("trustManager", new ReflectionParser<>(HttpBuilder::trustManager));
      register("connectionStrategy", new PropertyParser.Enum<>(ConnectionStrategy.values(), HttpBuilder::connectionStrategy));
      register("useHttpCache", new PropertyParser.Boolean<>(HttpBuilder::useHttpCache));
   }

   @Override
   public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
      HttpPluginBuilder plugin = target.addPlugin(HttpPluginBuilder::new);
      if (ctx.peek() instanceof SequenceStartEvent) {
         ctx.parseList(plugin, (ctx1, builder) -> {
            HttpBuilder http = builder.decoupledHttp();
            callSubBuilders(ctx1, http);
            builder.addHttp(http);
         });
      } else {
         callSubBuilders(ctx, plugin.http());
      }
   }

   private static void parseAddresses(Context ctx, HttpBuilder builder) throws ParserException {
      if (ctx.peek() instanceof ScalarEvent) {
         String value = ctx.expectEvent(ScalarEvent.class).getValue();
         if (value != null && !value.isEmpty()) {
            builder.addAddress(value);
         }
      } else {
         ctx.parseList(builder, ADDRESS_PARSER);
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

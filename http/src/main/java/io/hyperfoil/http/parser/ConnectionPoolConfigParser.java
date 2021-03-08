package io.hyperfoil.http.parser;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.hyperfoil.core.parser.AbstractParser;
import io.hyperfoil.core.parser.Context;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.parser.PropertyParser;
import io.hyperfoil.http.config.ConnectionPoolConfig;
import io.hyperfoil.http.config.HttpBuilder;

public class ConnectionPoolConfigParser extends AbstractParser<HttpBuilder, ConnectionPoolConfig.Builder> {
   public ConnectionPoolConfigParser() {
      register("core", new PropertyParser.Int<>(ConnectionPoolConfig.Builder::core));
      register("max", new PropertyParser.Int<>(ConnectionPoolConfig.Builder::max));
      register("buffer", new PropertyParser.Int<>(ConnectionPoolConfig.Builder::buffer));
      register("keepAliveTime", new PropertyParser.TimeMillis<>(ConnectionPoolConfig.Builder::keepAliveTime));
   }

   @Override
   public void parse(Context ctx, HttpBuilder http) throws ParserException {
      Event event = ctx.peek();
      if (event instanceof ScalarEvent) {
         String value = ((ScalarEvent) event).getValue();
         try {
            http.sharedConnections(Integer.parseInt(value));
         } catch (NumberFormatException e) {
            throw new ParserException(event, "Failed to parse as integer: " + value);
         }
         ctx.consumePeeked(event);
      } else if (event instanceof MappingStartEvent) {
         callSubBuilders(ctx, http.sharedConnections());
      } else {
         throw ctx.unexpectedEvent(event);
      }

   }
}

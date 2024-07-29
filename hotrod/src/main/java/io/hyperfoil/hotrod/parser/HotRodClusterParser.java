package io.hyperfoil.hotrod.parser;

import org.yaml.snakeyaml.events.ScalarEvent;

import io.hyperfoil.core.parser.AbstractParser;
import io.hyperfoil.core.parser.Context;
import io.hyperfoil.core.parser.Parser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.parser.PropertyParser;
import io.hyperfoil.hotrod.config.HotRodClusterBuilder;

public class HotRodClusterParser extends AbstractParser<HotRodClusterBuilder, HotRodClusterBuilder> {
   static HotRodClusterParser INSTANCE = new HotRodClusterParser();

   public HotRodClusterParser() {
      register("uri", new PropertyParser.String<>(HotRodClusterBuilder::uri));
      register("caches", new CachesParser());
   }

   @Override
   public void parse(Context ctx, HotRodClusterBuilder target) throws ParserException {
      callSubBuilders(ctx, target);
   }

   private static class CachesParser implements Parser<HotRodClusterBuilder> {
      @Override
      public void parse(Context ctx, HotRodClusterBuilder target) throws ParserException {
         ctx.parseList(target, this::parseItem);
      }

      private void parseItem(Context context, HotRodClusterBuilder builder) throws ParserException {
         ScalarEvent event = context.expectEvent(ScalarEvent.class);
         builder.addCache(event.getValue());
      }
   }
}

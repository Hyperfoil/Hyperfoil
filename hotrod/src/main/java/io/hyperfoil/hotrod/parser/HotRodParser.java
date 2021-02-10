package io.hyperfoil.hotrod.parser;

import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.core.parser.Context;
import io.hyperfoil.core.parser.Parser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.hotrod.config.HotRodClusterBuilder;
import io.hyperfoil.hotrod.config.HotRodPluginBuilder;

public class HotRodParser implements Parser<BenchmarkBuilder> {
   @Override
   public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
      HotRodPluginBuilder plugin = target.addPlugin(HotRodPluginBuilder::new);
      if (ctx.peek() instanceof SequenceStartEvent) {
         ctx.parseList(plugin, (ctx1, builder) -> {
            HotRodClusterBuilder hotRod = builder.addCluster();
            HotRodClusterParser.INSTANCE.parse(ctx1, hotRod);
         });
      } else {
         HotRodClusterParser.INSTANCE.parse(ctx, plugin.addCluster());
      }
   }
}

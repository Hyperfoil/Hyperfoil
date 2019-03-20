package io.hyperfoil.core.parser;

import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.hyperfoil.api.config.SLABuilder;

class PercentileLimitsParser implements Parser<SLABuilder> {
   @Override
   public void parse(Context ctx, SLABuilder target) throws ParserException {
      ctx.parseList(target, this::parseLimit);
   }

   private void parseLimit(Context ctx, SLABuilder builder) throws ParserException {
      ctx.expectEvent(MappingStartEvent.class);
      ScalarEvent percentile = ctx.expectEvent(ScalarEvent.class);
      ScalarEvent responseTime = ctx.expectEvent(ScalarEvent.class);
      builder.addPercentileLimit(Double.parseDouble(percentile.getValue()), io.hyperfoil.util.Util.parseToNanos(responseTime.getValue()));
      ctx.expectEvent(MappingEndEvent.class);
   }
}

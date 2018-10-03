package io.sailrocket.core.parser;

import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.sailrocket.core.builders.SLABuilder;

public class PercentileLimitsParser implements Parser<SLABuilder> {
   @Override
   public void parse(Context ctx, SLABuilder target) throws ConfigurationParserException {
      ctx.parseList(target, this::parseLimit);
   }

   public void parseLimit(Context ctx, SLABuilder builder) throws ConfigurationParserException {
      ScalarEvent percentile = ctx.expectEvent(ScalarEvent.class);
      ScalarEvent responseTime = ctx.expectEvent(ScalarEvent.class);
      builder.addPercentileLimit(Double.parseDouble(percentile.getValue()), Long.parseLong(responseTime.getValue()));
      ctx.expectEvent(MappingEndEvent.class);
   }
}

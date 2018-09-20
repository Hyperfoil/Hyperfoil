package io.sailrocket.core.parser;

import java.util.Iterator;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.sailrocket.core.builders.SLABuilder;

public class PercentileLimitsParser extends BaseParser<SLABuilder> {
   @Override
   public void parse(Iterator<Event> events, SLABuilder target) throws ConfigurationParserException {
      parseList(events, target, this::parseLimit);
   }

   public void parseLimit(Iterator<Event> events, SLABuilder builder) throws ConfigurationParserException {
      ScalarEvent percentile = expectEvent(events, ScalarEvent.class);
      ScalarEvent responseTime = expectEvent(events, ScalarEvent.class);
      builder.addPercentileLimit(Double.parseDouble(percentile.getValue()), Long.parseLong(responseTime.getValue()));
   }
}

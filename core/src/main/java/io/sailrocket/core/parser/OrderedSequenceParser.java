package io.sailrocket.core.parser;

import org.yaml.snakeyaml.events.ScalarEvent;

import io.sailrocket.core.builders.ScenarioBuilder;
import io.sailrocket.core.builders.SequenceBuilder;

/**
 * This provides a syntax-sugar automatically following one sequence with another
 */
class OrderedSequenceParser implements Parser<ScenarioBuilder> {
   @Override
   public void parse(Context ctx, ScenarioBuilder target) throws ConfigurationParserException {
      ctx.pushVar(null);
      ctx.parseList(target, this::parseSequence);
      ctx.popVar(null);
   }

   private void parseSequence(Context ctx, ScenarioBuilder target) throws ConfigurationParserException {
      ScalarEvent sequenceNameEvent = ctx.expectEvent(ScalarEvent.class);
      SequenceBuilder lastBuilder = ctx.popVar(SequenceBuilder.class);
      SequenceBuilder sequenceBuilder;
      if (lastBuilder == null) {
         sequenceBuilder = target.initialSequence(sequenceNameEvent.getValue());
      } else {
         sequenceBuilder = target.sequence(sequenceNameEvent.getValue());
      }
      SequenceParser.parseSequence(ctx, sequenceBuilder);
      if (lastBuilder != null) {
         lastBuilder.step().nextSequence(sequenceNameEvent.getValue());
      }
      ctx.pushVar(sequenceBuilder);
   }
}

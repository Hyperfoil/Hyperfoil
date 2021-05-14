package io.hyperfoil.core.parser;

import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.config.SequenceBuilder;

/**
 * This provides a syntax-sugar automatically following one sequence with another
 */
class OrderedSequenceParser implements Parser<ScenarioBuilder> {
   @Override
   public void parse(Context ctx, ScenarioBuilder target) throws ParserException {
      ctx.pushVar(null);
      ctx.parseList(target, this::parseSequence);
      ctx.popVar(null);
   }

   private void parseSequence(Context ctx, ScenarioBuilder target) throws ParserException {
      ctx.expectEvent(MappingStartEvent.class);
      ScalarEvent sequenceNameEvent = ctx.expectEvent(ScalarEvent.class);
      SequenceBuilder lastBuilder = ctx.popVar(SequenceBuilder.class);
      SequenceParser.Supplier supplier = lastBuilder == null ? ScenarioBuilder::initialSequence : ScenarioBuilder::sequence;
      SequenceBuilder sequenceBuilder = SequenceParser.parseSequence(ctx, sequenceNameEvent.getValue(), target, supplier);

      if (lastBuilder != null) {
         lastBuilder.nextSequence(sequenceNameEvent.getValue());
      }
      ctx.pushVar(sequenceBuilder);
      ctx.expectEvent(MappingEndEvent.class);
   }
}

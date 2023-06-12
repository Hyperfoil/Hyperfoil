package io.hyperfoil.core.parser;

import java.util.function.BiConsumer;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.RelativeIteration;
import io.hyperfoil.api.config.PhaseReferenceDelay;
import io.hyperfoil.impl.Util;

class StartWithParser implements Parser<PhaseBuilder<?>> {
   private final BiConsumer<PhaseBuilder<?>, PhaseReferenceDelay> consumer;

   StartWithParser(BiConsumer<PhaseBuilder<?>, PhaseReferenceDelay> consumer) {
      this.consumer = consumer;
   }

   @Override
   public void parse(Context ctx, PhaseBuilder<?> target) throws ParserException {
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(ScalarEvent.class, SequenceStartEvent.class, MappingStartEvent.class);
      }
      Event event = ctx.peek();
      if (event instanceof ScalarEvent) {
         consumer.accept(target, new PhaseReferenceDelay(((ScalarEvent) event).getValue(), RelativeIteration.NONE, null, 0));
         ctx.consumePeeked(event);
      } else if (event instanceof MappingStartEvent) {
         StartWithBuilder swb = new StartWithBuilder();
         MappingParser.INSTANCE.parse(ctx, swb);
         if (swb.phase == null || swb.phase.isEmpty()) {
            throw new BenchmarkDefinitionException("Missing name in phase reference.");
         }
         consumer.accept(target, new PhaseReferenceDelay(swb.phase, swb.iteration, swb.fork, swb.delay));
      } else {
         throw ctx.unexpectedEvent(event);
      }
   }

   private static class StartWithBuilder {
      String phase;
      RelativeIteration iteration = RelativeIteration.NONE;
      String fork;
      long delay;
   }

   private static class MappingParser extends AbstractMappingParser<StartWithBuilder> {
      static MappingParser INSTANCE = new MappingParser();

      MappingParser() {
         register("phase", new PropertyParser.String<>((b, value) -> b.phase = value));
         register("iteration", new PropertyParser.String<>((b, value) -> b.iteration = RelativeIteration.valueOf(value.toUpperCase())));
         register("fork", new PropertyParser.String<>((b, value) -> b.fork = value));
         register("delay", new PropertyParser.String<>((b, value) -> b.delay = Util.parseToMillis(value)));
      }
   }
}

package io.hyperfoil.core.parser;

import java.util.function.BiConsumer;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.PhaseReference;
import io.hyperfoil.api.config.RelativeIteration;

class StartAfterParser implements Parser<PhaseBuilder> {
   private final BiConsumer<PhaseBuilder, PhaseReference> consumer;

   StartAfterParser(BiConsumer<PhaseBuilder, PhaseReference> consumer) {
      this.consumer = consumer;
   }

   @Override
   public void parse(Context ctx, PhaseBuilder target) throws ParserException {
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(ScalarEvent.class, SequenceStartEvent.class, MappingStartEvent.class);
      }
      Event event = ctx.peek();
      if (event instanceof SequenceStartEvent) {
         ctx.parseList(target, this::parseItem);
         return;
      }
      parseItem(ctx, target);
   }

   private void parseItem(Context ctx, PhaseBuilder target) throws ParserException {
      Event event = ctx.peek();
      if (event instanceof ScalarEvent) {
         consumer.accept(target, new PhaseReference(((ScalarEvent) event).getValue(), RelativeIteration.NONE, null));
         ctx.consumePeeked(event);
      } else if (event instanceof MappingStartEvent) {
         PhaseReferenceBuilder ms = new PhaseReferenceBuilder();
         MappingParser.INSTANCE.parse(ctx, ms);
         if (ms.phase == null || ms.phase.isEmpty()) {
            throw new BenchmarkDefinitionException("Missing name in phase reference.");
         }
         consumer.accept(target, new PhaseReference(ms.phase, ms.iteration, null));
      } else {
         throw ctx.unexpectedEvent(event);
      }
   }

   private static class PhaseReferenceBuilder {
      String phase;
      RelativeIteration iteration = RelativeIteration.NONE;
      String fork;
   }

   private static class MappingParser extends AbstractMappingParser<PhaseReferenceBuilder> {
      static MappingParser INSTANCE = new MappingParser();

      MappingParser() {
         register("phase", new PropertyParser.String<>((ms, value) -> ms.phase = value));
         register("iteration", new PropertyParser.String<>((ms, value) -> ms.iteration = RelativeIteration.valueOf(value.toUpperCase())));
         register("fork", new PropertyParser.String<>((ms, value) -> ms.fork = value));
      }
   }
}

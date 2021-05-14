package io.hyperfoil.core.parser;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.core.builders.ServiceLoadedContract;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;

class StepParser extends BaseReflectionParser implements Parser<BaseSequenceBuilder<?>> {
   private static final StepParser INSTANCE = new StepParser();

   public static StepParser instance() {
      return INSTANCE;
   }

   private StepParser() {}

   @Override
   public void parse(Context ctx, BaseSequenceBuilder<?> target) throws ParserException {
      Event firstEvent = ctx.next();
      @SuppressWarnings("rawtypes")
      ServiceLoadedBuilderProvider<StepBuilder> provider = new ServiceLoadedBuilderProvider<>(StepBuilder.class, target::stepBuilder, target);
      if (firstEvent instanceof ScalarEvent) {
         ServiceLoadedContract slc;
         String name = ((ScalarEvent) firstEvent).getValue();
         try {
            slc = provider.forName(name, null);
         } catch (BenchmarkDefinitionException e) {
            throw new ParserException(firstEvent, "Failed to instantiate step builder " + name, e);
         }
         slc.complete();
         return;
      } else if (!(firstEvent instanceof MappingStartEvent)) {
         throw ctx.unexpectedEvent(firstEvent);
      }

      ScalarEvent stepEvent = ctx.expectEvent(ScalarEvent.class);
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(ScalarEvent.class, MappingStartEvent.class, MappingEndEvent.class, SequenceStartEvent.class);
      }
      fillSLBP(ctx, stepEvent, provider);
      ctx.expectEvent(MappingEndEvent.class);
   }

}

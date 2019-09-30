package io.hyperfoil.core.parser;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.ServiceLoadedContract;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.StepCatalog;

class StepParser extends BaseReflectionParser implements Parser<BaseSequenceBuilder> {
   private static final StepParser INSTANCE = new StepParser();

   public static StepParser instance() {
      return INSTANCE;
   }

   private StepParser() {}

   @Override
   public void parse(Context ctx, BaseSequenceBuilder target) throws ParserException {
      Event firstEvent = ctx.next();
      StepCatalog catalog = target.step(StepCatalog.class);
      if (firstEvent instanceof ScalarEvent) {
         ScalarEvent stepEvent = (ScalarEvent) firstEvent;
         Object builder = invokeWithNoParams(catalog, stepEvent, stepEvent.getValue());
         if (builder instanceof ServiceLoadedContract) {
            ((ServiceLoadedContract) builder).complete();
         }
         return;
      } else if (!(firstEvent instanceof MappingStartEvent)) {
         throw ctx.unexpectedEvent(firstEvent);
      }

      ScalarEvent stepEvent = ctx.expectEvent(ScalarEvent.class);
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(ScalarEvent.class, MappingStartEvent.class, MappingEndEvent.class, SequenceStartEvent.class);
      }
      invokeWithParameters(ctx, catalog, stepEvent);
      ctx.expectEvent(MappingEndEvent.class);
   }

}

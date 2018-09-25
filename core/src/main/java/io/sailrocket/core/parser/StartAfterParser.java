package io.sailrocket.core.parser;

import java.util.function.BiConsumer;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.sailrocket.core.builders.PhaseBuilder;

public class StartAfterParser implements Parser<PhaseBuilder> {
   private final BiConsumer<PhaseBuilder, String> consumer;

   public StartAfterParser(BiConsumer<PhaseBuilder, String> consumer) {
      this.consumer = consumer;
   }

   @Override
   public void parse(Context ctx, PhaseBuilder target) throws ConfigurationParserException {
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(ScalarEvent.class, SequenceStartEvent.class);
      }
      Event event = ctx.next();
      if (event instanceof ScalarEvent) {
         consumer.accept(target, ((ScalarEvent) event).getValue());
      } else if (event instanceof SequenceStartEvent) {
         while (ctx.hasNext()) {
            Event next = ctx.next();
            if (next instanceof SequenceEndEvent) {
               return;
            } else if (next instanceof ScalarEvent) {
               consumer.accept(target, ((ScalarEvent) next).getValue());
            } else {
               throw ctx.unexpectedEvent(event);
            }
         }
      } else {
         throw ctx.unexpectedEvent(event);
      }
   }
}

package io.sailrocket.core.parser;

import java.util.Iterator;
import java.util.function.BiConsumer;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.sailrocket.core.builders.PhaseBuilder;

public class StartAfterParser extends BaseParser<PhaseBuilder> {
   private final BiConsumer<PhaseBuilder, String> consumer;

   public StartAfterParser(BiConsumer<PhaseBuilder, String> consumer) {
      this.consumer = consumer;
   }

   @Override
   public void parse(Iterator<Event> events, PhaseBuilder target) throws ConfigurationParserException {
      if (!events.hasNext()) {
         throw noMoreEvents(ScalarEvent.class, SequenceStartEvent.class);
      }
      Event event = events.next();
      if (event instanceof ScalarEvent) {
         consumer.accept(target, ((ScalarEvent) event).getValue());
      } else if (event instanceof SequenceStartEvent) {
         while (events.hasNext()) {
            Event next = events.next();
            if (next instanceof SequenceEndEvent) {
               return;
            } else if (next instanceof ScalarEvent) {
               consumer.accept(target, ((ScalarEvent) next).getValue());
            } else {
               throw unexpectedEvent(event);
            }
         }
      } else {
         throw unexpectedEvent(event);
      }
   }
}

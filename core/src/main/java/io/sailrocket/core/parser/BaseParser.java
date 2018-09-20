package io.sailrocket.core.parser;

import java.util.Arrays;
import java.util.Iterator;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

abstract class BaseParser<T> implements Parser<T> {
   protected <E extends Event> E expectEvent(Iterator<Event> events, Class<E> eventClazz) throws ConfigurationParserException {
       if (events.hasNext()) {
           Event event = events.next();
           if (!eventClazz.isInstance(event)) {
               throw new ConfigurationParserException(event, "Expected " + eventClazz + ", got " + event);
           }
           return (E) event;
       } else {
           throw noMoreEvents(eventClazz);
       }
   }

   @SafeVarargs
   protected final ConfigurationParserException noMoreEvents(Class<? extends Event>... eventClazzes) {
       return new ConfigurationParserException("Expected one of " + Arrays.toString(eventClazzes) + " but there are no more events.");
   }

   protected ConfigurationParserException unexpectedEvent(Event event) {
       return new ConfigurationParserException(event, "Unexpected event " + event);
   }

   protected <LI> void parseList(Iterator<Event> events, LI target, ListItemParser<LI> consumer) throws ConfigurationParserException {
      parseList(events, target, consumer, (event, t) -> {
         throw new ConfigurationParserException(event, "Expected mapping, got " + event.getValue());
      });
   }

   protected <LI> void parseList(Iterator<Event> events, LI target, ListItemParser<LI> consumer, SingleListItemParser<LI> singleConsumer) throws ConfigurationParserException {
      if (!events.hasNext()) {
         throw noMoreEvents(SequenceStartEvent.class, ScalarEvent.class);
      }
      Event event = events.next();
      if (event instanceof SequenceStartEvent) {
         parseListHeadless(events, target, consumer, singleConsumer);
      } else if (event instanceof ScalarEvent) {
         // if the value is null/empty we can consider this an empty list
         String value = ((ScalarEvent) event).getValue();
         if (value != null && !value.isEmpty()) {
            throw new ConfigurationParserException(event, "Expected a sequence, got " + value);
         }
      }
   }

   protected <LI> void parseListHeadless(Iterator<Event> events, LI target, ListItemParser<LI> consumer, SingleListItemParser<LI> singleConsumer) throws ConfigurationParserException {
      while (events.hasNext()) {
          Event next = events.next();
          if (next instanceof SequenceEndEvent) {
              break;
          } else if (next instanceof MappingStartEvent) {
             consumer.accept(events, target);
          } else if (next instanceof ScalarEvent) {
             singleConsumer.accept((ScalarEvent) next, target);
          } else {
              throw unexpectedEvent(next);
          }
      }
   }

   protected interface ListItemParser<LI> {
       void accept(Iterator<Event> events, LI target) throws ConfigurationParserException;
   }

   protected interface SingleListItemParser<LI> {
       void accept(ScalarEvent event, LI target) throws ConfigurationParserException;
   }
}

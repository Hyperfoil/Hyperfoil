package io.sailrocket.core.parser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

public class Context {
   private final Map<String, Anchor> anchors = new HashMap<>();
   private final Iterator<Event> events;
   private Event peeked;

   public Context(Iterator<Event> events) {
      this.events = events;
   }

   public boolean hasNext() {
      return events.hasNext();
   }

   public Event next() {
      if (peeked == null) {
         return events.next();
      } else {
         Event tmp = peeked;
         peeked = null;
         return tmp;
      }
   }

   public Event peek() {
      peeked = events.next();
      return peeked;
   }

   public void setAnchor(Event event, String anchor, Object object) throws ConfigurationParserException {
      Anchor prev = anchors.putIfAbsent(anchor, new Anchor(event, object));
      if (prev != null) {
         throw new ConfigurationParserException(event, "Anchor " + anchor + " already defined on line "
               + prev.source.getStartMark().getLine() + ", column " + prev.source.getStartMark().getColumn());
      }
   }

   public Object getAnchor(Event event, String alias) throws ConfigurationParserException {
      Anchor anchor = anchors.get(alias);
      if (anchor == null) {
         throw new ConfigurationParserException(event, "There's no anchor for " + alias + ", available are "
               + anchors.keySet().stream().sorted().collect(Collectors.toList()));
      }
      return anchor.object;
   }

   protected <E extends Event> E expectEvent(Class<E> eventClazz) throws ConfigurationParserException {
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

   protected <LI> void parseList(LI target, ListItemParser<LI> consumer) throws ConfigurationParserException {
      parseList(target, consumer, (event, t) -> {
         throw new ConfigurationParserException(event, "Expected mapping, got " + event.getValue());
      });
   }

   protected <LI> void parseList(LI target, ListItemParser<LI> consumer, SingleListItemParser<LI> singleConsumer) throws ConfigurationParserException {
      if (!events.hasNext()) {
         throw noMoreEvents(SequenceStartEvent.class, ScalarEvent.class);
      }
      Event event = events.next();
      if (event instanceof SequenceStartEvent) {
         parseListHeadless(target, consumer, singleConsumer);
      } else if (event instanceof ScalarEvent) {
         // if the value is null/empty we can consider this an empty list
         String value = ((ScalarEvent) event).getValue();
         if (value != null && !value.isEmpty()) {
            throw new ConfigurationParserException(event, "Expected a sequence, got " + value);
         }
      }
   }

   protected <LI> void parseListHeadless(LI target, ListItemParser<LI> consumer, SingleListItemParser<LI> singleConsumer) throws ConfigurationParserException {
      while (events.hasNext()) {
         Event next = events.next();
         if (next instanceof SequenceEndEvent) {
            break;
         } else if (next instanceof MappingStartEvent) {
            consumer.accept(this, target);
         } else if (next instanceof ScalarEvent) {
            singleConsumer.accept((ScalarEvent) next, target);
         } else {
            throw unexpectedEvent(next);
         }
      }
   }

   protected interface ListItemParser<LI> {
      void accept(Context ctx, LI target) throws ConfigurationParserException;
   }

   protected interface SingleListItemParser<LI> {
      void accept(ScalarEvent event, LI target) throws ConfigurationParserException;
   }

   private static class Anchor {
      final Event source;
      final Object object;

      private Anchor(Event source, Object object) {
         this.source = source;
         this.object = object;
      }
   }
}

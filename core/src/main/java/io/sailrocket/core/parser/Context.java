package io.sailrocket.core.parser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.core.builders.Rewritable;


class Context {
   private final Map<String, Anchor> anchors = new HashMap<>();
   private final Iterator<Event> events;
   private Event peeked;
   private final Stack<Object> vars = new Stack<>();

   Context(Iterator<Event> events) {
      this.events = events;
   }

   boolean hasNext() {
      return events.hasNext();
   }

   Event next() {
      if (peeked == null) {
         return events.next();
      } else {
         Event tmp = peeked;
         peeked = null;
         return tmp;
      }
   }

   Event peek() {
      peeked = events.next();
      return peeked;
   }

   void setAnchor(Event event, String anchor, Object object) throws ConfigurationParserException {
      Objects.requireNonNull(anchor);
      Objects.requireNonNull(object);
      Anchor prev = anchors.putIfAbsent(anchor + ":" + object.getClass().getName(), new Anchor(event, object));
      if (prev != null) {
         throw new ConfigurationParserException(event, "Anchor " + anchor + " already defined on " + ConfigurationParserException.location(prev.source));
      }
   }

   <T> T getAnchor(Event event, String alias, Class<T> clazz) throws ConfigurationParserException {
      Objects.requireNonNull(alias);
      Anchor anchor = anchors.get(alias + ":" + clazz.getName());
      if (anchor == null) {
         String prefix = alias + ":";
         for (String key : anchors.keySet()) {
            if (key.startsWith(prefix)) {
               Anchor similar = anchors.get(key);
               throw new ConfigurationParserException(event, "There is no anchor for " + alias + " with type " + clazz +
                     " but there is another anchor " + key + " on " + ConfigurationParserException.location(similar.source));
            }
         }
         throw new ConfigurationParserException(event, "There's no anchor for " + alias + ", available are "
               + anchors.keySet().stream().sorted().collect(Collectors.toList()));
      }
      if (!clazz.isInstance(anchor.object)) {
         throw new ConfigurationParserException(event, alias + " is anchored to unexpected type "
               + anchor.object.getClass() + " while we expect " + clazz + "; anchor is defined on "
               + ConfigurationParserException.location(anchor.source));
      }
      return (T) anchor.object;
   }

   <E extends Event> E expectEvent(Class<E> eventClazz) throws ConfigurationParserException {
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
   final ConfigurationParserException noMoreEvents(Class<? extends Event>... eventClazzes) {
      return new ConfigurationParserException("Expected one of " + Arrays.toString(eventClazzes) + " but there are no more events.");
   }

   ConfigurationParserException unexpectedEvent(Event event) {
      return new ConfigurationParserException(event, "Unexpected event " + event);
   }

   <LI> void parseList(LI target, Parser<LI> consumer) throws ConfigurationParserException {
      parseList(target, consumer, (event, t) -> {
         throw new ConfigurationParserException(event, "Expected mapping, got " + event.getValue());
      });
   }

   <LI> void parseList(LI target, Parser<LI> consumer, SingleListItemParser<LI> singleConsumer) throws ConfigurationParserException {
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

   <LI> void parseListHeadless(LI target, Parser<LI> consumer, SingleListItemParser<LI> singleConsumer) throws ConfigurationParserException {
      while (events.hasNext()) {
         Event next = events.next();
         if (next instanceof SequenceEndEvent) {
            break;
         } else if (next instanceof MappingStartEvent) {
            consumer.parse(this, target);
         } else if (next instanceof ScalarEvent) {
            singleConsumer.accept((ScalarEvent) next, target);
         } else {
            throw unexpectedEvent(next);
         }
      }
   }

   <A extends Rewritable<A>> void parseAliased(Class<A> aliasType, A target, Parser<A> parser) throws ConfigurationParserException {
      Event event = next();
      try {
         if (event instanceof MappingStartEvent) {
            String anchor = ((MappingStartEvent) event).getAnchor();
            if (anchor != null) {
               setAnchor(event, anchor, target);
            }
            parser.parse(this, target);
         } else if (event instanceof AliasEvent) {
            String anchor = ((AliasEvent) event).getAnchor();
            A aliased = getAnchor(event, anchor, aliasType);
            target.readFrom(aliased);
         } else {
            throw unexpectedEvent(event);
         }
      } catch (BenchmarkDefinitionException e) {
         throw new ConfigurationParserException(event, "Error in benchmark builders", e);
      }
   }


   void pushVar(Object var) {
      vars.push(var);
   }

   <T> T popVar(Class<T> clazz) {
      Object top = vars.peek();
      if (clazz != null && top != null && !clazz.isInstance(top)) {
         throw new IllegalStateException("On the top of the stack is " + top);
      }
      return (T) vars.pop();
   }

   <T> T peekVar(Class<T> clazz) {
      Object top = vars.peek();
      if (clazz != null && top != null && !clazz.isInstance(top)) {
         throw new IllegalStateException("On the top of the stack is " + top);
      }
      return (T) top;
   }

   interface SingleListItemParser<LI> {
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

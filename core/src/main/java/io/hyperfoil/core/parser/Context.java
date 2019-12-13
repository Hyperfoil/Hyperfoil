package io.hyperfoil.core.parser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Rewritable;

class Context {
   private final Map<String, Anchor> anchors = new HashMap<>();
   private final Iterator<Event> events;
   private Event peeked;
   private final Stack<Object> vars = new Stack<>();

   Context(Iterator<Event> events) {
      this.events = events;
   }

   private ParserException transformException(org.yaml.snakeyaml.parser.ParserException e) {
      StringBuilder sb = new StringBuilder("YAML is malformed at line ")
            .append(e.getProblemMark().getLine() + 1)
            .append(", column ").append(e.getProblemMark().getColumn() + 1)
            .append(": ").append(e.getProblem());
      return new ParserException(sb.toString());
   }

   private String translate(Class<? extends Event> clazz) {
      if (clazz == MappingStartEvent.class) {
         return "<start of mapping>";
      } else if (clazz == MappingEndEvent.class) {
         return "<end of mapping>";
      } else if (clazz == SequenceStartEvent.class) {
         return "<start of sequence>";
      } else if (clazz == SequenceEndEvent.class) {
         return "<end of sequence>";
      } else if (clazz == ScalarEvent.class) {
         return "<scalar value>";
      }
      return clazz.getSimpleName();
   }


   boolean hasNext() throws ParserException {
      try {
         return events.hasNext();
      } catch (org.yaml.snakeyaml.parser.ParserException e) {
         throw transformException(e);
      }
   }

   Event next() throws ParserException {
      if (peeked == null) {
         try {
            return events.next();
         } catch (org.yaml.snakeyaml.parser.ParserException e) {
            throw transformException(e);
         }
      } else {
         Event tmp = peeked;
         peeked = null;
         return tmp;
      }
   }

   Event peek() throws ParserException {
      if (peeked == null) {
         try {
            peeked = events.next();
         } catch (org.yaml.snakeyaml.parser.ParserException e) {
            throw transformException(e);
         }
      }
      return peeked;
   }

   void consumePeeked(Event event) throws ParserException {
      Event peekedEvent = next();
      assert peekedEvent == event;
   }


   void setAnchor(Event event, String anchor, Object object) throws ParserException {
      Objects.requireNonNull(anchor);
      Objects.requireNonNull(object);
      Anchor prev = anchors.putIfAbsent(anchor + ":" + object.getClass().getName(), new Anchor(event, object));
      if (prev != null) {
         throw new ParserException(event, "Anchor '" + anchor + "' already defined on '" + ParserException.location(prev.source) + "'");
      }
   }

   <T> T getAnchor(Event event, String alias, Class<T> clazz) throws ParserException {
      Objects.requireNonNull(alias);
      Anchor anchor = anchors.get(alias + ":" + clazz.getName());
      if (anchor == null) {
         String prefix = alias + ":";
         for (String key : anchors.keySet()) {
            if (key.startsWith(prefix)) {
               Anchor similar = anchors.get(key);
               throw new ParserException(event, "There is no anchor for '" + alias + "' with type '" + clazz +
                     "' but there is another anchor '" + key + "' on '" + ParserException.location(similar.source) + "'");
            }
         }
         throw new ParserException(event, "There's no anchor for '" + alias + "', available are '"
               + anchors.keySet().stream().sorted().collect(Collectors.toList()) + "'");
      }
      if (!clazz.isInstance(anchor.object)) {
         throw new ParserException(event, "'" + alias + "' is anchored to unexpected type '"
               + anchor.object.getClass() + "' while we expect '" + clazz + "'; anchor is defined on '"
               + ParserException.location(anchor.source) + "'");
      }
      @SuppressWarnings("unchecked")
      T object = (T) anchor.object;
      return object;
   }

   <E extends Event> E expectEvent(Class<E> eventClazz) throws ParserException {
      if (hasNext()) {
         Event event = next();
         if (!eventClazz.isInstance(event)) {
            throw new ParserException(event, "Expected " + translate(eventClazz) + ", got " + translate(event.getClass()) + ": " + event);
         }
         @SuppressWarnings("unchecked")
         E expectedEvent = (E) event;
         return expectedEvent;
      } else {
         throw noMoreEvents(eventClazz);
      }
   }

   @SafeVarargs
   final ParserException noMoreEvents(Class<? extends Event>... eventClazzes) {
      String expected = Stream.of(eventClazzes).map(this::translate).collect(Collectors.joining(", "));
      return new ParserException("Expected one of [" + expected + "] but there are no more events.");
   }

   ParserException unexpectedEvent(Event event) {
      return new ParserException(event, "Unexpected " + translate(event.getClass()) + ": " + event);
   }

   <LI> void parseList(LI target, Parser<LI> consumer) throws ParserException {
      if (!hasNext()) {
         throw noMoreEvents(SequenceStartEvent.class, ScalarEvent.class);
      }
      Event event = next();
      if (event instanceof SequenceStartEvent) {
         String anchor = ((SequenceStartEvent) event).getAnchor();
         if (anchor != null) {
            setAnchor(event, anchor, target);
         }
         while (hasNext()) {
            Event itemEvent = peek();
            if (itemEvent instanceof SequenceEndEvent) {
               consumePeeked(itemEvent);
               break;
            } else {
               try {
                  consumer.parse(this, target);
               } catch (ParserException e) {
                  throw e;
               } catch (Exception e) {
                  throw new ParserException(itemEvent, "Benchmark parsing error", e);
               }
            }
         }
      } else if (event instanceof ScalarEvent) {
         // if the value is null/empty we can consider this an empty list
         String value = ((ScalarEvent) event).getValue();
         if (value != null && !value.isEmpty()) {
            throw new ParserException(event, "Expected a sequence, got '" + value + "'");
         }
      } else {
         throw unexpectedEvent(event);
      }
   }

   <A extends Rewritable<A>> void parseAliased(Class<A> aliasType, A target, Parser<A> parser) throws ParserException {
      Event event = peek();
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
            consumePeeked(event);
         } else {
            throw unexpectedEvent(event);
         }
      } catch (BenchmarkDefinitionException e) {
         throw new ParserException(event, "Error in benchmark builders", e);
      }
   }

   <S> void parseMapping(S target, BuilderProvider<S> builderProvider) throws ParserException {
      expectEvent(MappingStartEvent.class);
      while (hasNext()) {
         Event next = next();
         if (next instanceof MappingEndEvent) {
            return;
         } else if (next instanceof ScalarEvent) {
            ScalarEvent event = (ScalarEvent) next;
            Parser<S> builder = builderProvider.apply(event);
            builder.parse(this, target);
         } else {
            throw unexpectedEvent(next);
         }
      }
      throw noMoreEvents(MappingEndEvent.class);
   }


   void pushVar(Object var) {
      vars.push(var);
   }

   <T> T popVar(Class<T> clazz) {
      Object top = vars.peek();
      if (clazz != null && top != null && !clazz.isInstance(top)) {
         throw new IllegalStateException("On the top of the stack is '" + top + "'");
      }
      @SuppressWarnings("unchecked")
      T popped = (T) vars.pop();
      return popped;
   }

   <T> T peekVar(Class<T> clazz) {
      Object top = vars.peek();
      if (clazz != null && top != null && !clazz.isInstance(top)) {
         throw new IllegalStateException("On the top of the stack is '" + top + "'");
      }
      @SuppressWarnings("unchecked")
      T peeked = (T) top;
      return peeked;
   }

   @FunctionalInterface
   public interface BuilderProvider<S> {
      Parser<S> apply(ScalarEvent event) throws ParserException;
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

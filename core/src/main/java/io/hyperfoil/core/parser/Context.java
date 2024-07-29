package io.hyperfoil.core.parser;

import java.util.Iterator;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

public class Context {
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

   public boolean hasNext() throws ParserException {
      try {
         return events.hasNext();
      } catch (org.yaml.snakeyaml.parser.ParserException e) {
         throw transformException(e);
      }
   }

   public Event next() throws ParserException {
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

   public Event peek() throws ParserException {
      if (peeked == null) {
         try {
            peeked = events.next();
         } catch (org.yaml.snakeyaml.parser.ParserException e) {
            throw transformException(e);
         }
      }
      return peeked;
   }

   public void consumePeeked(Event event) throws ParserException {
      Event peekedEvent = next();
      assert peekedEvent == event;
   }

   public <E extends Event> E expectEvent(Class<E> eventClazz) throws ParserException {
      if (hasNext()) {
         Event event = next();
         if (!eventClazz.isInstance(event)) {
            throw new ParserException(event,
                  "Expected " + translate(eventClazz) + ", got " + translate(event.getClass()) + ": " + event);
         }
         @SuppressWarnings("unchecked")
         E expectedEvent = (E) event;
         return expectedEvent;
      } else {
         throw noMoreEvents(eventClazz);
      }
   }

   @SafeVarargs
   public final ParserException noMoreEvents(Class<? extends Event>... eventClazzes) {
      String expected = Stream.of(eventClazzes).map(this::translate).collect(Collectors.joining(", "));
      return new ParserException("Expected one of [" + expected + "] but there are no more events.");
   }

   public ParserException unexpectedEvent(Event event) {
      return new ParserException(event, "Unexpected " + translate(event.getClass()) + ": " + event);
   }

   public <LI> void parseList(LI target, Parser<LI> consumer) throws ParserException {
      if (!hasNext()) {
         throw noMoreEvents(SequenceStartEvent.class, ScalarEvent.class);
      }
      Event event = next();
      if (event instanceof SequenceStartEvent) {
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

   public <S> void parseMapping(S target, BuilderProvider<S> builderProvider) throws ParserException {
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

   public void pushVar(Object var) {
      vars.push(var);
   }

   public <T> T popVar(Class<T> clazz) {
      Object top = vars.peek();
      if (clazz != null && top != null && !clazz.isInstance(top)) {
         throw new IllegalStateException("On the top of the stack is '" + top + "'");
      }
      @SuppressWarnings("unchecked")
      T popped = (T) vars.pop();
      return popped;
   }

   public <T> T peekVar(Class<T> clazz) {
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

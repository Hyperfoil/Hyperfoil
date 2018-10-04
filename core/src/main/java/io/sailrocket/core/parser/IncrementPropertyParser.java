package io.sailrocket.core.parser;

import java.util.function.Function;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

class IncrementPropertyParser<T, V> implements Parser<T> {
   final IncrementPropertyConsumer<T, V> consumer;
   final Function<String, V> conversion;

   IncrementPropertyParser(IncrementPropertyConsumer<T, V> consumer, Function<String, V> conversion) {
      this.consumer = consumer;
      this.conversion = conversion;
   }

   @Override
   public void parse(Context ctx, T target) throws ConfigurationParserException {
      Event event = ctx.peek();
      if (event instanceof ScalarEvent) {
         consumer.accept(target, conversion.apply(((ScalarEvent) event).getValue()), conversion.apply(null));
         ctx.consumePeeked(event);
      } else if (event instanceof MappingStartEvent) {
         MappedValue mv = new MappedValue();
         MappingParser.INSTANCE.parse(ctx, mv);
         consumer.accept(target, conversion.apply(mv.base), conversion.apply(mv.increment));
      } else {
         throw ctx.unexpectedEvent(event);
      }
   }

   private static class MappedValue {
      String base;
      String increment;
   }

   private static class MappingParser extends AbstractMappingParser<MappedValue> {
      static MappingParser INSTANCE = new MappingParser();

      MappingParser() {
         register("base", new PropertyParser.String<>((mv, value) -> mv.base = value));
         register("increment", new PropertyParser.String<>((mv, value) -> mv.increment = value));
      }
   }

   @FunctionalInterface
   interface IncrementPropertyConsumer<T, V> {
      void accept(T target, V base, V increment);
   }

   static class Double<T> extends IncrementPropertyParser<T, java.lang.Double> {
      Double(IncrementPropertyConsumer<T, java.lang.Double> consumer) {
         super(consumer, value -> value == null ? 0 : java.lang.Double.parseDouble(value));
      }
   }

   static class Int<T> extends IncrementPropertyParser<T, Integer> {
      Int(IncrementPropertyConsumer<T, Integer> consumer) {
         super(consumer, value -> value == null ? 0 : Integer.parseInt(value));
      }
   }
}

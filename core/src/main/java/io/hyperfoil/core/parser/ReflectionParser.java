package io.hyperfoil.core.parser;

import java.util.function.Function;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

public class ReflectionParser<T, S> extends BaseReflectionParser implements Parser<T> {
   private final Function<T, S> selector;

   public ReflectionParser(Function<T, S> selector) {
      this.selector = selector;
   }

   @Override
   public void parse(Context ctx, T target) throws ParserException {
      S builder = selector.apply(target);
      ctx.expectEvent(MappingStartEvent.class);
      while (ctx.hasNext()) {
         Event next = ctx.next();
         if (next instanceof MappingEndEvent) {
            return;
         } else if (next instanceof ScalarEvent) {
            invokeWithParameters(ctx, builder, (ScalarEvent) next);
         } else {
            throw ctx.unexpectedEvent(next);
         }
      }
      throw ctx.noMoreEvents(MappingEndEvent.class);
   }
}

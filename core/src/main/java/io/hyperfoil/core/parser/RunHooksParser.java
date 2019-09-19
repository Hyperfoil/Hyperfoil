package io.hyperfoil.core.parser;

import java.util.function.BiConsumer;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.RunHook;
import io.hyperfoil.core.hooks.ExecRunHook;

class RunHooksParser implements Parser<BenchmarkBuilder> {
   private final BiConsumer<BenchmarkBuilder, RunHook> consumer;

   public RunHooksParser(BiConsumer<BenchmarkBuilder, RunHook> consumer) {
      this.consumer = consumer;
   }

   @Override
   public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
      Event event = ctx.peek();
      if (event instanceof MappingStartEvent) {
         ctx.parseMapping(target, (builder, e) -> new RunHookParser(e.getValue()));
      } else if (event instanceof SequenceStartEvent) {
         ctx.parseList(target, this::parseRunHook);
      }
   }

   private void parseRunHook(Context ctx, BenchmarkBuilder target) throws ParserException {
      ctx.expectEvent(MappingStartEvent.class);
      String name = ctx.expectEvent(ScalarEvent.class).getValue();
      new RunHookParser(name).parse(ctx, target);
      ctx.expectEvent(MappingEndEvent.class);
   }

   private class RunHookParser implements Parser<BenchmarkBuilder> {
      private final String name;

      public RunHookParser(String name) {
         this.name = name;
      }

      @Override
      public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
         Event next = ctx.next();
         if (next instanceof ScalarEvent) {
            consumer.accept(target, new ExecRunHook(name, ((ScalarEvent) next).getValue()));
         } else {
            throw new BenchmarkDefinitionException("Malformed run hook definition, expecting inline command.");
         }
         // TODO: mapping through `exec`, or `java`...
      }
   }
}

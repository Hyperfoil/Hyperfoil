package io.hyperfoil.core.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PluginBuilder;
import io.hyperfoil.core.api.Plugin;

public class ErgonomicsParser implements Parser<BenchmarkBuilder> {
   private final Map<String, PluginTuple<?, ?>> subParsers = new HashMap<>();

   ErgonomicsParser() {
      ServiceLoader.load(Plugin.class).forEach(factory -> factory.enhanceErgonomics(this));
   }

   @Override
   public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
      ctx.parseMapping(target, event -> {
         PluginTuple<?, ?> tuple = subParsers.get(event.getValue());
         if (tuple == null) {
            throw new ParserException(event, "Invalid configuration label: '" + event.getValue() + "', expected one of " + subParsers.keySet());
         }
         return tuple;
      });
   }

   public <T extends PluginBuilder<E>, E> void register(String name, Class<T> plugin, Parser<E> parser) {
      PluginTuple<?, ?> prev = subParsers.putIfAbsent(name, new PluginTuple<>(plugin, parser));
      if (prev != null) {
         throw new IllegalStateException("Ergonomics property '" + name + "' already registered by " + prev.plugin.getName() + ", now trying to register by " + plugin.getName());
      }
   }

   private static class PluginTuple<T extends PluginBuilder<E>, E> implements Parser<BenchmarkBuilder> {
      final Class<T> plugin;
      final Parser<E> parser;

      private PluginTuple(Class<T> plugin, Parser<E> parser) {
         this.plugin = plugin;
         this.parser = parser;
      }

      @Override
      public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
         E ergonomics = target.plugin(this.plugin).ergonomics();
         parser.parse(ctx, ergonomics);
      }
   }
}

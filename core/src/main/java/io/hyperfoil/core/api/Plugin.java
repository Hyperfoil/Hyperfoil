package io.hyperfoil.core.api;

import java.util.ServiceLoader;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PluginConfig;
import io.hyperfoil.core.parser.ErgonomicsParser;
import io.hyperfoil.core.parser.Parser;
import io.netty.channel.EventLoop;

public interface Plugin {
   static Plugin lookup(PluginConfig config) {
      return ServiceLoader.load(Plugin.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(p -> p.configClass() == config.getClass())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing plugin for config " + config.getClass().getName()));
   }

   Class<? extends PluginConfig> configClass();

   String name();

   Parser<BenchmarkBuilder> parser();

   void enhanceErgonomics(ErgonomicsParser ergonomicsParser);

   PluginRunData createRunData(Benchmark benchmark, EventLoop[] executors, int agentId);
}

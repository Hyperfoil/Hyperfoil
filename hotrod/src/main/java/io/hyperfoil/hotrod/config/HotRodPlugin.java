package io.hyperfoil.hotrod.config;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PluginConfig;
import io.hyperfoil.core.api.Plugin;
import io.hyperfoil.core.api.PluginRunData;
import io.hyperfoil.core.parser.ErgonomicsParser;
import io.hyperfoil.core.parser.Parser;
import io.hyperfoil.hotrod.HotRodRunData;
import io.hyperfoil.hotrod.parser.HotRodParser;
import io.netty.channel.EventLoop;

@MetaInfServices(Plugin.class)
public class HotRodPlugin implements Plugin {

   @Override
   public Class<? extends PluginConfig> configClass() {
      return HotRodPluginConfig.class;
   }

   @Override
   public String name() {
      return "hotrod";
   }

   @Override
   public Parser<BenchmarkBuilder> parser() {
      return new HotRodParser();
   }

   @Override
   public void enhanceErgonomics(ErgonomicsParser ergonomicsParser) {

   }

   @Override
   public PluginRunData createRunData(Benchmark benchmark, EventLoop[] executors, int agentId) {
      return new HotRodRunData(benchmark, executors, agentId);
   }
}

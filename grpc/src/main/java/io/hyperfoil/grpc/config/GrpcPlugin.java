package io.hyperfoil.grpc.config;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PluginConfig;
import io.hyperfoil.core.api.Plugin;
import io.hyperfoil.core.api.PluginRunData;
import io.hyperfoil.core.parser.ErgonomicsParser;
import io.hyperfoil.core.parser.Parser;
import io.hyperfoil.grpc.GrpcRunData;
import io.hyperfoil.grpc.parser.GrpcParser;
import io.netty.channel.EventLoop;

@MetaInfServices(Plugin.class)
public class GrpcPlugin implements Plugin {
   @Override
   public Class<? extends PluginConfig> configClass() {
      return GrpcPluginConfig.class;
   }

   @Override
   public String name() {
      return "grpc";
   }

   @Override
   public Parser<BenchmarkBuilder> parser() {
      return new GrpcParser();
   }

   @Override
   public void enhanceErgonomics(ErgonomicsParser ergonomicsParser) {

   }

   @Override
   public PluginRunData createRunData(Benchmark benchmark, EventLoop[] executors, int agentId) {
      return new GrpcRunData(benchmark, executors);
   }
}

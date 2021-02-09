package io.hyperfoil.http.config;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.PluginConfig;
import io.hyperfoil.core.api.Plugin;
import io.hyperfoil.core.parser.ErgonomicsParser;
import io.hyperfoil.core.parser.PropertyParser;
import io.hyperfoil.http.HttpRunData;
import io.hyperfoil.http.api.FollowRedirect;
import io.hyperfoil.http.parser.HttpParser;
import io.netty.channel.EventLoop;

@MetaInfServices(Plugin.class)
public class HttpPlugin implements Plugin {
   @Override
   public Class<? extends PluginConfig> configClass() {
      return HttpPluginConfig.class;
   }

   @Override
   public String name() {
      return "http";
   }

   @Override
   public HttpParser parser() {
      return new HttpParser();
   }

   @Override
   public void enhanceErgonomics(ErgonomicsParser parser) {
      parser.register("repeatCookies", HttpPluginBuilder.class, new PropertyParser.Boolean<>(HttpErgonomics::repeatCookies));
      parser.register("userAgentFromSession", HttpPluginBuilder.class, new PropertyParser.Boolean<>(HttpErgonomics::userAgentFromSession));
      parser.register("autoRangeCheck", HttpPluginBuilder.class, new PropertyParser.Boolean<>(HttpErgonomics::autoRangeCheck));
      parser.register("stopOnInvalid", HttpPluginBuilder.class, new PropertyParser.Boolean<>(HttpErgonomics::stopOnInvalid));
      parser.register("followRedirect", HttpPluginBuilder.class, new PropertyParser.Enum<>(FollowRedirect.values(), HttpErgonomics::followRedirect));
   }

   @Override
   public HttpRunData createRunData(Benchmark benchmark, EventLoop[] executors, int agentId) {
      return new HttpRunData(benchmark, executors, agentId);
   }

}

package io.hyperfoil.http.config;

import java.util.Map;

import io.hyperfoil.api.config.PluginConfig;
import io.hyperfoil.api.config.Visitor;

public class HttpPluginConfig implements PluginConfig {
   private final Map<String, Http> http;
   @Visitor.Ignore
   private final Http defaultHttp;

   public HttpPluginConfig(Map<String, Http> http) {
      this.http = http;
      this.defaultHttp = http.values().stream().filter(Http::isDefault).findFirst().orElse(null);
   }

   public Map<String, Http> http() {
      return http;
   }

   public Http defaultHttp() {
      return defaultHttp;
   }
}

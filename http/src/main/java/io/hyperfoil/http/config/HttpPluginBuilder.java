package io.hyperfoil.http.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.PluginBuilder;
import io.hyperfoil.api.config.PluginConfig;

public class HttpPluginBuilder extends PluginBuilder<HttpErgonomics> {
   private HttpBuilder defaultHttp;
   private List<HttpBuilder> httpList = new ArrayList<>();
   private HttpErgonomics ergonomics = new HttpErgonomics(this);

   public HttpPluginBuilder(BenchmarkBuilder parent) {
      super(parent);
   }

   public static Collection<HttpBuilder> httpForTesting(BenchmarkBuilder benchmarkBuilder) {
      HttpPluginBuilder builder = benchmarkBuilder.plugin(HttpPluginBuilder.class);
      if (builder.defaultHttp == null) {
         return Collections.unmodifiableList(builder.httpList);
      } else if (builder.httpList.isEmpty()) {
         return Collections.singletonList(builder.defaultHttp);
      } else {
         ArrayList<HttpBuilder> list = new ArrayList<>(builder.httpList);
         list.add(builder.defaultHttp);
         return list;
      }
   }

   public HttpBuilder http() {
      if (defaultHttp == null) {
         defaultHttp = new HttpBuilder(this);
      }
      return defaultHttp;
   }

   public HttpBuilder http(String host) {
      HttpBuilder builder = new HttpBuilder(this).host(host);
      httpList.add(builder);
      return builder;
   }

   @Override
   public HttpErgonomics ergonomics() {
      return ergonomics;
   }

   @Override
   public void prepareBuild() {
      if (defaultHttp == null) {
         if (httpList.isEmpty()) {
            // may be removed in the future when we define more than HTTP connections
            throw new BenchmarkDefinitionException("No default HTTP target set!");
         } else if (httpList.size() == 1) {
            defaultHttp = httpList.iterator().next();
         }
      } else {
         if (httpList.stream().anyMatch(http -> http.authority().equals(defaultHttp.authority()))) {
            throw new BenchmarkDefinitionException("Ambiguous HTTP definition for "
                  + defaultHttp.authority() + ": defined both as default and non-default");
         }
         httpList.add(defaultHttp);
      }
      HashSet<String> authorities = new HashSet<>();
      for (HttpBuilder http : httpList) {
         if (!authorities.add(http.authority())) {
            throw new BenchmarkDefinitionException("Duplicit HTTP definition for " + http.authority());
         }
      }
      httpList.forEach(HttpBuilder::prepareBuild);
   }

   @Override
   public void addTags(Map<String, Object> tags) {
      if (defaultHttp != null) {
         Http defaultHttp = this.defaultHttp.build(true);
         tags.put("url", defaultHttp.protocol().scheme + "://" + defaultHttp.host() + ":" + defaultHttp.port());
         tags.put("protocol", defaultHttp.protocol().scheme);
      }
   }

   @Override
   public PluginConfig build() {
      Map<String, Http> httpMap = httpList.stream()
            .collect(Collectors.toMap(HttpBuilder::authority, http -> http.build(http == defaultHttp)));
      return new HttpPluginConfig(httpMap);
   }

   public boolean validateAuthority(String authority) {
      return authority == null && defaultHttp != null || httpList.stream().anyMatch(http -> http.authority().equals(authority));
   }

   public HttpBuilder getHttp(String authority) {
      if (authority == null && defaultHttp != null) {
         return defaultHttp;
      } else {
         return httpList.stream().filter(http -> http.authority().equals(authority)).findFirst().orElse(null);
      }
   }

   public HttpBuilder decoupledHttp() {
      return new HttpBuilder(this);
   }

   public void addHttp(HttpBuilder builder) {
      if (builder.authority() == null) {
         throw new BenchmarkDefinitionException("Missing hostname!");
      }
      httpList.add(builder);
   }
}

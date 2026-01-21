package io.hyperfoil.grpc.config;

import java.util.Map;

import io.hyperfoil.api.config.PluginConfig;

public class GrpcPluginConfig implements PluginConfig {

   private final Map<String, Grpc> grpcByAuthority;

   public GrpcPluginConfig(Map<String, Grpc> byAuthority) {
      this.grpcByAuthority = byAuthority;
   }

   public Map<String, Grpc> grpcByAuthority() {
      return grpcByAuthority;
   }
}

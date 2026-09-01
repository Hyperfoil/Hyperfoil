package io.hyperfoil.grpc.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.PluginBuilder;
import io.hyperfoil.api.config.PluginConfig;

public class GrpcPluginBuilder extends PluginBuilder<GrpcErgonomics> {
   private GrpcBuilder defaultGrpc;
   private List<GrpcBuilder> grpcList = new ArrayList<>();
   private GrpcErgonomics ergonomics = new GrpcErgonomics(this);

   public GrpcPluginBuilder(BenchmarkBuilder parent) {
      super(parent);
   }

   public GrpcBuilder decoupledGrpc() {
      return new GrpcBuilder(this);
   }

   public void addGrpc(GrpcBuilder builder) {
      if (builder.authority() == null) {
         throw new BenchmarkDefinitionException("Missing hostname!");
      }
      grpcList.add(builder);
   }

   @Override
   public GrpcErgonomics ergonomics() {
      return ergonomics;
   }

   @Override
   public void prepareBuild() {
      if (defaultGrpc == null) {
         if (grpcList.isEmpty()) {
            throw new BenchmarkDefinitionException("No default gRPC target set!");
         } else if (grpcList.size() == 1) {
            defaultGrpc = grpcList.iterator().next();
         }
      } else {
         if (grpcList.stream().anyMatch(grpc -> grpc.authority().equals(defaultGrpc.authority()))) {
            throw new BenchmarkDefinitionException("Ambiguous gRPC definition for "
                  + defaultGrpc.authority() + ": defined both as default and non-default");
         }
         grpcList.add(defaultGrpc);
      }
      grpcList.forEach(GrpcBuilder::prepareBuild);
   }

   @Override
   public PluginConfig build() {
      Map<String, Grpc> byName = new HashMap<>();
      Map<String, Grpc> byAuthority = new HashMap<>();
      BenchmarkData data = this.parent.data();
      for (GrpcBuilder builder : grpcList) {
         Grpc grpc = builder.build(builder == defaultGrpc, data);
         Grpc previous = builder.name() == null ? null : byName.put(builder.name(), grpc);
         if (previous != null) {
            throw new BenchmarkDefinitionException("Duplicate gRPC endpoint name " + builder.name() + ": used both for "
                  + grpc.originalDestination() + " and " + previous.originalDestination());
         }
         previous = byAuthority.put(builder.authority(), grpc);
         if (previous != null && builder.name() == null) {
            throw new BenchmarkDefinitionException("Duplicate gRPC endpoint for authority " + builder.authority());
         }
      }
      return new GrpcPluginConfig(byAuthority);
   }

   public GrpcBuilder grpc() {
      if (defaultGrpc == null) {
         defaultGrpc = new GrpcBuilder(this);
      }
      return defaultGrpc;
   }
}

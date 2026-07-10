package io.hyperfoil.grpc.config;

import java.net.MalformedURLException;
import java.net.URL;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;

public class GrpcBuilder implements BuilderBase<GrpcBuilder> {
   private final GrpcPluginBuilder parent;
   private String name;
   private Grpc grpc;
   private String originalDestination;
   private String host;
   private int port = 80;
   private int maxStreams = -1;
   private int sharedConnections = 1;
   private ProtoConfig.Builder protoConfig = new ProtoConfig.Builder(this);

   public GrpcBuilder(GrpcPluginBuilder parent) {
      this.parent = parent;
   }

   public GrpcBuilder name(String name) {
      this.name = name;
      return this;
   }

   String name() {
      return name;
   }

   String authority() {
      if (host() == null) {
         return null;
      } else if (port == -1) {
         return host();
      } else {
         return host() + ":" + portOrDefault();
      }
   }

   public String host() {
      return host;
   }

   public int portOrDefault() {
      if (port != -1) {
         return port;
      } else {
         throw new BenchmarkDefinitionException("No port nor protocol has been defined");
      }
   }

   public GrpcBuilder host(String destination) {
      if (this.host != null) {
         throw new BenchmarkDefinitionException("Duplicate 'host'. Are you missing '-'s?");
      }
      URL result;
      String spec;
      int schemeEnd = destination.indexOf("://");
      if (schemeEnd < 0) {
         spec = "http://" + destination;
         originalDestination = destination;
      } else {
         spec = destination;
         originalDestination = destination.substring(schemeEnd + 3);
      }
      try {
         result = new URL(spec);
      } catch (MalformedURLException e) {
         throw new BenchmarkDefinitionException("Failed to parse host:port", e);
      }
      URL url = result;
      this.host = url.getHost();
      this.port = url.getPort();
      if (url.getFile() != null && !url.getFile().isEmpty()) {
         throw new BenchmarkDefinitionException("Host must not contain any path: " + destination);
      }
      return this;
   }

   public GrpcBuilder port(int port) {
      if (this.port > 0) {
         throw new BenchmarkDefinitionException("Duplicate 'port'");
      }
      this.port = port;
      return this;
   }

   public GrpcPluginBuilder endGrpc() {
      return parent;
   }

   public ProtoConfig.Builder proto() {
      return protoConfig;
   }

   public GrpcBuilder sharedConnections(int sharedConnections) {
      this.sharedConnections = sharedConnections;
      return this;
   }

   public GrpcBuilder maxStreams(int maxStreams) {
      this.maxStreams = maxStreams;
      return this;
   }

   public Grpc build(boolean isDefault, BenchmarkData data) {
      if (grpc != null) {
         return grpc;
      }
      if (originalDestination == null) {
         originalDestination = host;
         if (port >= 0) {
            originalDestination += ":" + port;
         }
      }
      return grpc = new Grpc(name, isDefault, originalDestination, host, port, maxStreams, sharedConnections,
            protoConfig.build(data));
   }
}

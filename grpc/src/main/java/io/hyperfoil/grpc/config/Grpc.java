package io.hyperfoil.grpc.config;

import java.io.Serializable;

public class Grpc implements Serializable {
   private final String name;
   private final boolean isDefault;
   private final String originalDestination;
   private final String host;
   private final int port;
   private final int maxStreams;
   private final int sharedConnections;
   private final ProtoConfig protoConfig;

   public Grpc(String name, boolean isDefault, String originalDestination, String host, int port, int maxStreams,
         int sharedConnections, ProtoConfig protoConfig) {
      this.name = name;
      this.isDefault = isDefault;
      this.originalDestination = originalDestination;
      this.host = host;
      this.port = port;
      this.maxStreams = maxStreams;
      this.sharedConnections = sharedConnections;
      this.protoConfig = protoConfig;
   }

   public ProtoConfig protoConfig() {
      return protoConfig;
   }

   public String name() {
      return name;
   }

   public String host() {
      return host;
   }

   public int port() {
      return port;
   }

   public int maxStreams() {
      return maxStreams;
   }

   public int sharedConnections() {
      return sharedConnections;
   }

   public boolean isDefault() {
      return isDefault;
   }

   public String originalDestination() {
      return originalDestination;
   }
}

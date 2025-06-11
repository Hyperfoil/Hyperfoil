package io.hyperfoil.grpc.proto;

import java.util.Map;

import com.google.protobuf.Descriptors;

import io.hyperfoil.grpc.api.GrpcProtoDescriptions;

public final class GrpcProtoDescriptionsImpl implements GrpcProtoDescriptions {

   private final Map<String, Descriptors.FileDescriptor> protoByAuthority;
   private final String defaultAuthority;

   public GrpcProtoDescriptionsImpl(String defaultAuthority, Map<String, Descriptors.FileDescriptor> protoByAuthority) {
      // create a Map.of from protoByAuthority
      this.protoByAuthority = protoByAuthority;
      this.defaultAuthority = defaultAuthority;
   }

   @Override
   public Descriptors.FileDescriptor byAuthority(String authority) {
      if (authority == null) {
         authority = defaultAuthority;
      }
      return protoByAuthority.get(authority);
   }
}

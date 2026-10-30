package io.hyperfoil.grpc.api;

import com.google.protobuf.Descriptors;

import io.hyperfoil.api.session.Session;

public interface GrpcProtoDescriptions extends Session.Resource {
   Session.ResourceKey<GrpcProtoDescriptions> KEY = new Session.ResourceKey<>() {
   };

   Descriptors.FileDescriptor byAuthority(String authority);
}

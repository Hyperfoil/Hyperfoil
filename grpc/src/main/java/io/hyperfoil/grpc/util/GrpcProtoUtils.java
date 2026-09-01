package io.hyperfoil.grpc.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

public class GrpcProtoUtils {

   public static byte[] encodeWith(Descriptors.Descriptor messageDescriptor, String json) throws Exception {
      DynamicMessage.Builder builder = DynamicMessage.newBuilder(messageDescriptor);
      JsonFormat.parser().merge(json, builder);
      DynamicMessage dynamicMessage = builder.build();
      return dynamicMessage.toByteArray();
   }
}

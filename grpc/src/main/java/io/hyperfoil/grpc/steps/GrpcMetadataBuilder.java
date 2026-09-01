package io.hyperfoil.grpc.steps;

import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.core.generators.StringGeneratorImplBuilder;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableFunction;
import io.vertx.grpc.client.GrpcClientRequest;

public final class GrpcMetadataBuilder extends PairBuilder.OfString {

   private static final class MetadataPairAppender implements SerializableBiConsumer<Session, GrpcClientRequest<?, ?>> {

      private final String key;
      private final SerializableFunction<Session, String> value;

      private MetadataPairAppender(String key, StringGeneratorBuilder value) {
         this.key = key;
         this.value = value.build();
      }

      @Override
      public void accept(Session session, GrpcClientRequest<?, ?> request) {
         request.headers().add(key, value.apply(session));
      }

      // create static factory methods for the static valued cases too
      public static MetadataPairAppender constant(GrpcMetadataBuilder builder, String key, String value) {
         return new MetadataPairAppender(key, new StringGeneratorImplBuilder<>(builder.parent).pattern(value));
      }

      public static MetadataPairAppender variable(String key, StringGeneratorBuilder value) {
         return new MetadataPairAppender(key, value);
      }
   }

   private final GrpcRequestStepBuilder parent;

   public GrpcMetadataBuilder(GrpcRequestStepBuilder parent) {
      this.parent = parent;
   }

   @Override
   public void accept(String key, String value) {
      parent.metadataAppender(MetadataPairAppender.constant(this, key, value));
   }

   public GrpcRequestStepBuilder endMetadata() {
      return parent;
   }
}
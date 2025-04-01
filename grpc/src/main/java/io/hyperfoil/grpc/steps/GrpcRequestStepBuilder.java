package io.hyperfoil.grpc.steps;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.core.generators.StringGeneratorImplBuilder;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.grpc.resource.GrpcRequestContext;
import io.vertx.grpc.client.GrpcClientRequest;

@MetaInfServices(StepBuilder.class)
@Name("grpcRequest")
public class GrpcRequestStepBuilder extends BaseStepBuilder<GrpcRequestStepBuilder> {
   private StringGeneratorBuilder call;
   private StringGeneratorBuilder data;
   private StringGeneratorBuilder authority;
   private List<SerializableBiConsumer<Session, GrpcClientRequest<?, ?>>> metadataAppenders = new ArrayList<>();

   public GrpcMetadataBuilder metadata() {
      return new GrpcMetadataBuilder(this);
   }

   void metadataAppender(SerializableBiConsumer<Session, GrpcClientRequest<?, ?>> metadataPairAppender) {
      metadataAppenders.add(metadataPairAppender);
   }

   public GrpcRequestStepBuilder data(String data) {
      var builder = new StringGeneratorImplBuilder<>(this);
      builder.pattern(data);
      this.data = builder;
      return builder.end();
   }

   public GrpcRequestStepBuilder call(String call) {
      var builder = new StringGeneratorImplBuilder<>(this);
      builder.pattern(call);
      this.call = builder;
      return builder.end();
   }

   public GrpcRequestStepBuilder authority(String authority) {
      var builder = new StringGeneratorImplBuilder<>(this);
      builder.pattern(authority);
      this.authority = builder;
      return builder.end();
   }

   private boolean isCacheable() {
      return isConstantPattern(call) && isConstantPattern(data) && isConstantPattern(authority);
   }

   private static boolean isConstantPattern(StringGeneratorBuilder builder) {
      if (builder == null) {
         return true;
      }
      return StringGeneratorImplBuilder.class.cast(builder).isConstantPattern();
   }

   @Override
   public List<Step> build() {
      int stepId = StatisticsStep.nextId();
      var cacheable = isCacheable();
      var call = this.call.build();
      var data = this.data.build();
      var authority = this.authority != null ? this.authority.build() : null;
      var requestCtxKey = new GrpcRequestContext.KEY();
      var prepareRequest = new GrpcPrepareRequestStep(stepId, authority, requestCtxKey);
      var sendRequest = new GrpcSendRequestStep(stepId, call, data, requestCtxKey,
            metadataAppenders.toArray(new SerializableBiConsumer[0]), cacheable);
      var receivedResponseHeader = new GrpcReceivedResponseStep(requestCtxKey);
      return List.of(prepareRequest, sendRequest, receivedResponseHeader);
   }
}

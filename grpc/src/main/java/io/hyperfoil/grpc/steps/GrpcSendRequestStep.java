package io.hyperfoil.grpc.steps;

import java.util.Objects;

import com.google.protobuf.Descriptors;

import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.grpc.api.GrpcProtoDescriptions;
import io.hyperfoil.grpc.resource.GrpcRequestContext;
import io.hyperfoil.grpc.util.GrpcProtoUtils;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.client.GrpcClientRequest;
import io.vertx.grpc.client.GrpcClientResponse;
import io.vertx.grpc.common.ServiceName;

public class GrpcSendRequestStep extends StatisticsStep implements SLA.Provider {
   private final SerializableBiConsumer<Session, GrpcClientRequest<?, ?>>[] metadataAppenders;
   private SerializableFunction<Session, String> call;
   private SerializableFunction<Session, String> data;
   private GrpcRequestContext.KEY requestCtxKey;
   private boolean cacheableRequest;
   private transient GrpcRequestData cachedRequestData;

   public GrpcSendRequestStep(int stepId,
         SerializableFunction<Session, String> call,
         SerializableFunction<Session, String> data,
         GrpcRequestContext.KEY requestCtxKey,
         SerializableBiConsumer<Session, GrpcClientRequest<?, ?>>[] metadataAppenders,
         boolean cacheableRequest) {
      super(stepId);
      this.call = call;
      this.data = data;
      this.requestCtxKey = requestCtxKey;
      this.metadataAppenders = metadataAppenders;
      this.cacheableRequest = cacheableRequest;
   }

   @Override
   public SLA[] sla() {
      return new SLA[0];
   }

   @Override
   public boolean invoke(Session session) {
      GrpcRequestContext requestCtx = session.getResource(requestCtxKey);
      return requestCtx.sendRequest(this);
   }

   public GrpcRequestData createGrpcRequestData(Session session, String authority) {
      GrpcRequestData requestData;
      if (!cacheableRequest || (requestData = this.cachedRequestData) == null) {
         requestData = GrpcRequestData.requestDataOf(this, session, authority);
         // it's fine if racy: it's an immutable class, which means that's safe published.
         // at worse we will have two identical copies of the same data
         if (cacheableRequest) {
            this.cachedRequestData = requestData;
         }
      }
      return requestData;
   }

   public static final class GrpcRequestData {
      private final ServiceName serviceName;
      private final String methodName;

      // TODO a client-side stream can just be an array of buffers here
      private final Buffer data;
      private final SerializableBiConsumer<Session, GrpcClientRequest<?, ?>>[] metadataAppenders;

      private GrpcRequestData(ServiceName serviceName, String methodName, Buffer data,
            SerializableBiConsumer<Session, GrpcClientRequest<?, ?>>[] metadataAppenders) {
         this.serviceName = serviceName;
         this.methodName = methodName;
         this.data = data;
         this.metadataAppenders = metadataAppenders;
      }

      public static GrpcRequestData requestDataOf(GrpcSendRequestStep step, Session session, String authority) {
         assert step.cacheableRequest;
         Descriptors.FileDescriptor descriptor = session.getResource(GrpcProtoDescriptions.KEY).byAuthority(authority);
         String call = step.call.apply(session);
         int startOfServiceName = call.indexOf('.');
         int startOfMethodName = call.indexOf('.', startOfServiceName + 1);
         String packageName = call.substring(0, startOfServiceName);
         assert Objects.equals(packageName, descriptor.getPackage());
         String serviceName = call.substring(startOfServiceName + 1, startOfMethodName);
         Descriptors.ServiceDescriptor serviceByName = descriptor.findServiceByName(serviceName);
         String methodName = call.substring(startOfMethodName + 1);
         Descriptors.MethodDescriptor methodByName = serviceByName.findMethodByName(methodName);
         try {
            Buffer dataBuffer = Buffer.buffer(GrpcProtoUtils.encodeWith(methodByName.getInputType(), step.data.apply(session)));
            return new GrpcRequestData(ServiceName.create(packageName, serviceName), methodName, dataBuffer,
                  step.metadataAppenders);
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      public Future<GrpcClientResponse<Buffer, Buffer>> sendRequest(GrpcClientRequest<Buffer, Buffer> request,
            Session session) {
         request.serviceName(serviceName);
         request.methodName(methodName);
         request.encoding("identity");
         for (SerializableBiConsumer<Session, GrpcClientRequest<?, ?>> metadataAppender : metadataAppenders) {
            metadataAppender.accept(session, request);
         }
         // TODO we are not handling any send statistics here
         request.end(data.slice(), null);
         return request.response();
      }
   }
}

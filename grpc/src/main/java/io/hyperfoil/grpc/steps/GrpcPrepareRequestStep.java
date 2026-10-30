package io.hyperfoil.grpc.steps;

import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.grpc.resource.GrpcRequestContext;

/**
 * This step is responsible for both preparing the request and acquiring the grpc Connection
 */
public class GrpcPrepareRequestStep extends StatisticsStep implements ResourceUtilizer {

   private SerializableFunction<Session, String> authority;
   private GrpcRequestContext.KEY requestCtxKey;

   public GrpcPrepareRequestStep(int stepId,
         SerializableFunction<Session, String> authority,
         GrpcRequestContext.KEY requestCtxKey) {
      super(stepId);
      this.authority = authority;
      this.requestCtxKey = requestCtxKey;
   }

   @Override
   public boolean invoke(Session session) {
      String authority = this.authority == null ? null : this.authority.apply(session);
      return GrpcRequestContext.prepareRequest(session, authority, requestCtxKey);
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(requestCtxKey, GrpcRequestContext::new);
   }
}

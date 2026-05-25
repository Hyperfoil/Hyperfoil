package io.hyperfoil.grpc.steps;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.grpc.resource.GrpcRequestContext;

public class GrpcReceivedResponseStep implements Step {

   private GrpcRequestContext.KEY requestCtxKey;

   public GrpcReceivedResponseStep(GrpcRequestContext.KEY requestCtxKey) {
      this.requestCtxKey = requestCtxKey;
   }

   @Override
   public boolean invoke(Session session) {
      GrpcRequestContext requestContext = session.getResource(requestCtxKey);
      if (requestContext.response == null || requestContext.responseBuffer == null) {
         return false;
      }
      // TODO add status handling, if any, but we need to verify that we can return back the connection to the pool
      requestContext.acquiredClient.release();
      requestContext.reset();
      return true;
   }
}
package io.hyperfoil.grpc.steps;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.grpc.resource.GrpcRequestContext;

public class GrpcReceivedResponseDataStep implements Step {
    private GrpcRequestContext.KEY requestCtxKey;

    public GrpcReceivedResponseDataStep(GrpcRequestContext.KEY requestCtxKey) {
        this.requestCtxKey = requestCtxKey;
    }

    @Override
    public boolean invoke(Session session) {
        GrpcRequestContext requestContext = session.getResource(requestCtxKey);
        if (requestContext.responseBuffer == null) {
            return false;
        }
        requestContext.acquiredClient.release();
        requestContext.reset();
        return true;
    }
}

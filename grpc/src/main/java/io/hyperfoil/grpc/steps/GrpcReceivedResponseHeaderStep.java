package io.hyperfoil.grpc.steps;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.grpc.resource.GrpcRequestContext;

public class GrpcReceivedResponseHeaderStep implements Step {

    private GrpcRequestContext.KEY requestCtxKey;

    public GrpcReceivedResponseHeaderStep(GrpcRequestContext.KEY requestCtxKey) {
        this.requestCtxKey = requestCtxKey;
    }

    @Override
    public boolean invoke(Session session) {
        GrpcRequestContext requestContext = session.getResource(requestCtxKey);
        if (requestContext.response == null) {
            return false;
        }
        // TODO add status handling, if any, but we need to verify that we can return back the connection to the pool
        requestContext.response.handler(buffer -> {
            requestContext.responseBuffer = buffer;
            session.proceed();
        });
        assert requestContext.responseBuffer == null;
        return true;
    }
}
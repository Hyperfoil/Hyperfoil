package io.sailrocket.core.client;

import io.netty.buffer.ByteBuf;
import io.sailrocket.core.api.SequenceContext;

@Deprecated
public class RequestContext {

    public SequenceContext sequenceContext;
//    public final String path;
//    public final ByteBuf payload;

    public RequestContext(SequenceContext sequenceContext, String path) {
        this(sequenceContext, path, null);
    }

    public RequestContext(SequenceContext sequenceContext, String path, ByteBuf payload) {
        this.sequenceContext = sequenceContext;
//        this.path = path;
//        this.payload = payload;
    }


}

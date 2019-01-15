package io.hyperfoil.api.http;

import java.io.Serializable;

import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.connection.Request;

public interface RawBytesHandler extends Serializable {
   void accept(Request request, ByteBuf byteBuf);
}

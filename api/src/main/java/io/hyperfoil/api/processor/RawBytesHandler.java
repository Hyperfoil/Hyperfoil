package io.hyperfoil.api.processor;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.connection.Request;
import io.netty.buffer.ByteBuf;

public interface RawBytesHandler extends Serializable {
   void onRequest(Request request, ByteBuf buf, int offset, int length);

   void onResponse(Request request, ByteBuf buf, int offset, int length, boolean isLastPart);

   interface Builder extends BuilderBase<Builder> {
      RawBytesHandler build();
   }
}

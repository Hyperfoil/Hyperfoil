package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.connection.Request;

public interface RawBytesHandler extends Serializable {
   void accept(Request request, ByteBuf byteBuf, int offset, int length, boolean isLastPart);

   interface Builder extends BuilderBase<Builder> {
      RawBytesHandler build();
   }
}

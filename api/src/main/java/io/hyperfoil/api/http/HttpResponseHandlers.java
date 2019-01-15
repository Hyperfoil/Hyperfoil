package io.hyperfoil.api.http;

import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.connection.ResponseHandlers;

public interface HttpResponseHandlers extends ResponseHandlers {
   void handleStatus(Request request, int status, String reason);

   void handleHeader(Request request, String header, String value);

   void handleBodyPart(Request request, ByteBuf buf);

   boolean hasRawBytesHandler();

   void handleRawBytes(Request request, ByteBuf buf);
}

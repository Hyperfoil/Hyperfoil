package io.sailrocket.api.http;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.connection.ResponseHandlers;

public interface HttpResponseHandlers extends ResponseHandlers {
   void handleStatus(Request request, int status, String reason);

   void handleHeader(Request request, String header, String value);

   void handleBodyPart(Request request, ByteBuf buf);

   boolean hasRawBytesHandler();

   void handleRawBytes(Request request, ByteBuf buf);
}

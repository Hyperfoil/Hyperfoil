package io.hyperfoil.api.http;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.ResponseHandlers;
import io.netty.buffer.ByteBuf;

public interface HttpResponseHandlers extends ResponseHandlers<HttpRequest> {
   void handleStatus(HttpRequest request, int status, String reason);

   void handleHeader(HttpRequest request, String header, String value);

   void handleBodyPart(HttpRequest request, ByteBuf buf);

   boolean hasRawBytesHandler();

   void handleRawBytes(HttpRequest request, ByteBuf buf);
}

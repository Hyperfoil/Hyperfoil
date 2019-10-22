package io.hyperfoil.api.http;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.ResponseHandlers;
import io.netty.buffer.ByteBuf;

public interface HttpResponseHandlers extends ResponseHandlers<HttpRequest> {
   void handleStatus(HttpRequest request, int status, String reason);

   void handleHeader(HttpRequest request, CharSequence header, CharSequence value);

   void handleBodyPart(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart);

   boolean hasRawBytesHandler();

   void handleRawBytes(HttpRequest request, ByteBuf buf);
}

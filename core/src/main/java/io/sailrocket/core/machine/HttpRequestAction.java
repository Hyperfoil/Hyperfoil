package io.sailrocket.core.machine;

import java.util.function.BiConsumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;

public class HttpRequestAction implements Action {
   private final HttpMethod method;
   private final Function<Session, String> pathGenerator;
   private final Function<Session, ByteBuf> bodyGenerator;
   private final BiConsumer<Session, BiConsumer<String, String>> headerAppender;
   private HttpResponseState handler;

   public HttpRequestAction(HttpMethod method,
                            Function<Session, String> pathGenerator,
                            Function<Session, ByteBuf> bodyGenerator,
                            BiConsumer<Session, BiConsumer<String, String>> headerAppender) {
      this.method = method;
      this.pathGenerator = pathGenerator;
      this.bodyGenerator = bodyGenerator;
      this.headerAppender = headerAppender;
   }

   public void setHandler(HttpResponseState handler) {
      this.handler = handler;
   }

   @Override
   public State invoke(Session session) {
      // TODO alloc!
      HttpRequest request = session.getHttpClientPool().request(method, pathGenerator.apply(session));
      if (headerAppender != null) {
         headerAppender.accept(session, request::putHeader);
      }

      // alloc-free below
      request.statusHandler(session.intHandler(handler, HttpResponseState.HANDLE_STATUS));
      request.exceptionHandler(session.exceptionHandler(handler, HttpResponseState.HANDLE_EXCEPTION));
      request.bodyHandler(session.objectHandler(handler, HttpResponseState.HANDLE_BODY));
      request.endHandler(session.objectHandler(handler, HttpResponseState.HANDLE_END));
      if (bodyGenerator == null) {
         request.end();
      } else {
         request.end(bodyGenerator.apply(session));
      }
      return handler;
   }
}

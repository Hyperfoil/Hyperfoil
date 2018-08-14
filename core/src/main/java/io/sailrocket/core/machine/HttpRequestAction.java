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
   private final BiConsumer<Session, HttpRequest> headerAppender;
   private final HttpResponseState handler;

   public HttpRequestAction(HttpMethod method,
                            Function<Session, String> pathGenerator,
                            Function<Session, ByteBuf> bodyGenerator,
                            BiConsumer<Session, HttpRequest> headerAppender,
                            HttpResponseState handler) {
      this.method = method;
      this.pathGenerator = pathGenerator;
      this.bodyGenerator = bodyGenerator;
      this.headerAppender = headerAppender;
      this.handler = handler;
   }

   @Override
   public void invoke(Session session) {
      // TODO alloc!
      ByteBuf body = bodyGenerator == null ? null : bodyGenerator.apply(session);
      HttpRequest request = session.getHttpClientPool().request(method, pathGenerator.apply(session), body);
      if (headerAppender != null) {
         headerAppender.accept(session, request);
      }

      // alloc-free below
      request.statusHandler(session.intHandler(handler, HttpResponseState.HANDLE_STATUS));
      request.exceptionHandler(session.exceptionHandler(handler, HttpResponseState.HANDLE_EXCEPTION));
      request.bodyHandler(session.objectHandler(handler, HttpResponseState.HANDLE_BODY));
      request.endHandler(session.objectHandler(handler, HttpResponseState.HANDLE_END));
      request.end();
   }
}

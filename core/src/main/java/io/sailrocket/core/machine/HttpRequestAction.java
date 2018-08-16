package io.sailrocket.core.machine;

import java.util.function.BiConsumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpRequestAction implements Action {
   private static final Logger log = LoggerFactory.getLogger(HttpRequestAction.class);

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
      ByteBuf body = bodyGenerator == null ? null : bodyGenerator.apply(session);
      String path = pathGenerator.apply(session);
      // TODO alloc!
      HttpRequest request = session.getHttpClientPool().request(method, path, body);
      if (headerAppender != null) {
         headerAppender.accept(session, request);
      }

      // alloc-free below
      request.statusHandler(session.intHandler(handler, HttpResponseState.HANDLE_STATUS));
      request.headerHandler(session.biHandler(handler, HttpResponseState.HANDLE_HEADER));
      request.exceptionHandler(session.exceptionHandler(handler, HttpResponseState.HANDLE_EXCEPTION));
      request.bodyPartHandler(session.objectHandler(handler, HttpResponseState.HANDLE_BODY_PART));
      request.endHandler(session.voidHandler(handler, HttpResponseState.HANDLE_END));

      log.trace("HTTP {} to {}", method, path);
      request.end();
   }
}

package io.sailrocket.core.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.sailrocket.api.http.HttpRequest;
import io.sailrocket.api.session.Session;
import io.sailrocket.function.SerializableBiConsumer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CookieAppender implements SerializableBiConsumer<Session, HttpRequest> {
   private static final Logger log = LoggerFactory.getLogger(CookieAppender.class);

   @Override
   public void accept(Session session, HttpRequest httpRequest) {
      CookieStore cookies = session.getResource(CookieStore.COOKIES);
      if (cookies == null) {
         log.error("No cookie store in the session. Did you add CookieRecorder?");
         return;
      }
      String cookie = cookies.getCookie(httpRequest.connection().address());
      if (cookie != null) {
         httpRequest.putHeader(HttpHeaderNames.COOKIE.toString(), cookie);
      }
   }
}

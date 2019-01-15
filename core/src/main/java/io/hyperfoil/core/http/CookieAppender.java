package io.hyperfoil.core.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiConsumer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CookieAppender implements SerializableBiConsumer<Session, HttpRequestWriter> {
   private static final Logger log = LoggerFactory.getLogger(CookieAppender.class);

   @Override
   public void accept(Session session, HttpRequestWriter httpRequest) {
      CookieStore cookies = session.getResource(CookieStore.COOKIES);
      if (cookies == null) {
         log.error("No cookie store in the session. Did you add CookieRecorder?");
         return;
      }
      String cookie = cookies.getCookie(httpRequest.connection().host());
      if (cookie != null) {
         httpRequest.putHeader(HttpHeaderNames.COOKIE.toString(), cookie);
      }
   }
}

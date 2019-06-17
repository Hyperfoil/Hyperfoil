package io.hyperfoil.core.http;

import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiConsumer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CookieAppender implements SerializableBiConsumer<Session, HttpRequestWriter> {
   private static final Logger log = LoggerFactory.getLogger(CookieAppender.class);

   @Override
   public void accept(Session session, HttpRequestWriter writer) {
      CookieStore cookies = session.getResource(CookieStore.COOKIES);
      if (cookies == null) {
         log.error("No cookie store in the session. Did you add CookieRecorder?");
         return;
      }
      cookies.appendCookies(writer);
   }
}

package io.hyperfoil.http.cookie;

import io.hyperfoil.http.api.HttpRequestWriter;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiConsumer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CookieAppender implements SerializableBiConsumer<Session, HttpRequestWriter> {
   private static final Logger log = LogManager.getLogger(CookieAppender.class);

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

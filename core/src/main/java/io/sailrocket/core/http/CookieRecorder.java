package io.sailrocket.core.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.http.HeaderExtractor;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.ResourceUtilizer;

public class CookieRecorder implements HeaderExtractor, ResourceUtilizer {
   @Override
   public void extractHeader(Request request, String header, String value) {
      if (HttpHeaderNames.SET_COOKIE.regionMatches(true, 0, header, 0, Math.min(header.length(), HttpHeaderNames.SET_COOKIE.length()))) {
         CookieStore cookies = request.session.getResource(CookieStore.COOKIES);
         cookies.setCookie(request.connection().host(), value);
      }
   }

   @Override
   public void reserve(Session session) {
      if (session.getResource(CookieStore.COOKIES) == null) {
         session.declareResource(CookieStore.COOKIES, new CookieStore());
      }
   }
}

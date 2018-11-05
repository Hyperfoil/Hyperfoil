package io.sailrocket.core.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.sailrocket.api.collection.RequestQueue;
import io.sailrocket.api.http.HeaderExtractor;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.ResourceUtilizer;

public class CookieRecorder implements HeaderExtractor, ResourceUtilizer {
   @Override
   public void extractHeader(RequestQueue.Request request, String header, String value, Session session) {
      if (HttpHeaderNames.SET_COOKIE.regionMatches(true, 0, header, 0, Math.min(header.length(), HttpHeaderNames.SET_COOKIE.length()))) {
         CookieStore cookies = session.getResource(CookieStore.COOKIES);
         cookies.setCookie(request.request.connection().address(), value);
      }
   }

   @Override
   public void reserve(Session session) {
      if (session.getResource(CookieStore.COOKIES) == null) {
         session.declareResource(CookieStore.COOKIES, new CookieStore());
      }
   }
}

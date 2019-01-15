package io.hyperfoil.core.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.HeaderExtractor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.api.ResourceUtilizer;

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

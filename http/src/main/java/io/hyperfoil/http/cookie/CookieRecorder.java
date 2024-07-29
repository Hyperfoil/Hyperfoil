package io.hyperfoil.http.cookie;

import io.hyperfoil.http.api.HttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.hyperfoil.http.api.HeaderHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class CookieRecorder implements HeaderHandler, ResourceUtilizer {
   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      if (HttpHeaderNames.SET_COOKIE.regionMatches(true, 0, header, 0, Math.min(header.length(), HttpHeaderNames.SET_COOKIE.length()))) {
         CookieStore cookies = request.session.getResource(CookieStore.COOKIES);
         cookies.setCookie(request.connection().host(), request.path, value);
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareResources().add(CookieStore.COOKIES, CookieStore::new, true);
   }
}

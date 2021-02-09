package io.hyperfoil.http.api;

import io.hyperfoil.api.session.Session;

public interface HttpCache extends Session.Resource {
   Session.ResourceKey<HttpCache> KEY = new Session.ResourceKey<>() {};

   void beforeRequestHeaders(HttpRequest request);

   void requestHeader(HttpRequest request, CharSequence header, CharSequence value);

   boolean isCached(HttpRequest request, HttpRequestWriter writer);

   void responseHeader(HttpRequest request, CharSequence header, CharSequence value);

   void tryStore(HttpRequest request);

   void invalidate(CharSequence authority, CharSequence path);

   // mostly for testing
   int size();

   void clear();

   interface Record {}

   static HttpCache get(Session session) {
      return session.getResource(KEY);
   }
}

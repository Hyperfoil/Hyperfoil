package io.hyperfoil.api.http;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.HttpRequestWriter;

public interface HttpCache {

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

}

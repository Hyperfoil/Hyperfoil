package io.hyperfoil.http.api;

public interface HttpRequestWriter {
   HttpConnection connection();

   HttpRequest request();

   void putHeader(CharSequence header, CharSequence value);
}

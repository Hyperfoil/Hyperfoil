package io.hyperfoil.api.connection;

public interface HttpRequestWriter {
   HttpConnection connection();

   HttpRequest request();

   void putHeader(CharSequence header, CharSequence value);
}

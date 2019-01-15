package io.hyperfoil.api.connection;

public interface HttpRequestWriter {
   Connection connection();

   void putHeader(CharSequence header, CharSequence value);
}

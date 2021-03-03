package io.hyperfoil.http.api;

public interface ConnectionConsumer {
   void accept(HttpConnection connection);
}

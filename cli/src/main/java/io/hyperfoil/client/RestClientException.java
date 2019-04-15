package io.hyperfoil.client;

public class RestClientException extends RuntimeException {
   public RestClientException(String message) {
      super(message);
   }

   public RestClientException(Throwable cause) {
      super(cause);
   }
}

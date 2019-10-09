package io.hyperfoil.client;

public class RestClientException extends RuntimeException {
   public RestClientException(String message) {
      super(message);
   }

   public RestClientException(Throwable cause) {
      super(cause);
   }

   public RestClientException(String message, Throwable cause) {
      super(message, cause);
   }
}

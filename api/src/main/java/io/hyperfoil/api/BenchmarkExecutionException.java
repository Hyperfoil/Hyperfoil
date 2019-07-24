package io.hyperfoil.api;

public class BenchmarkExecutionException extends Exception {
   public BenchmarkExecutionException(String message) {
      super(message);
   }

   public BenchmarkExecutionException(String message, Throwable cause) {
      super(message, cause);
   }
}

package io.sailrocket.api;

public interface HeaderExtractor {
   default void beforeHeaders(Session session) {
   }

   void extractHeader(String header, String value, Session session);

   default void afterHeaders(Session session) {
   }
}

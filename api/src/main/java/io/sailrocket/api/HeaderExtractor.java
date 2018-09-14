package io.sailrocket.api;

import java.io.Serializable;

public interface HeaderExtractor extends Serializable {
   default void beforeHeaders(Session session) {
   }

   void extractHeader(String header, String value, Session session);

   default void afterHeaders(Session session) {
   }
}

package io.sailrocket.api.http;

import java.io.Serializable;

import io.sailrocket.api.session.Session;

public interface HeaderExtractor extends Serializable {
   default void beforeHeaders(Session session) {
   }

   void extractHeader(String header, String value, Session session);

   default void afterHeaders(Session session) {
   }
}

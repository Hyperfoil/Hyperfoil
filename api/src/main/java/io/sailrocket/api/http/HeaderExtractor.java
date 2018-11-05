package io.sailrocket.api.http;

import java.io.Serializable;

import io.sailrocket.api.collection.RequestQueue;
import io.sailrocket.api.session.Session;

public interface HeaderExtractor extends Serializable {
   default void beforeHeaders(Session session) {
   }

   void extractHeader(RequestQueue.Request request, String header, String value, Session session);

   default void afterHeaders(Session session) {
   }
}

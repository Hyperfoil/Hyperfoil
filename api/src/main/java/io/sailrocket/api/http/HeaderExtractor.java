package io.sailrocket.api.http;

import java.io.Serializable;

import io.sailrocket.api.connection.Request;

public interface HeaderExtractor extends Serializable {
   default void beforeHeaders(Request request) {
   }

   void extractHeader(Request request, String header, String value);

   default void afterHeaders(Request request) {
   }
}

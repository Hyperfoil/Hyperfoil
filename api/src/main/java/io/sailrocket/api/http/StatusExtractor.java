package io.sailrocket.api.http;

import java.io.Serializable;

import io.sailrocket.api.config.ServiceLoadedBuilder;
import io.sailrocket.api.connection.Request;

public interface StatusExtractor extends Serializable {
   void setStatus(Request request, int status);

   interface BuilderFactory extends ServiceLoadedBuilder.Factory<StatusExtractor> {}
}

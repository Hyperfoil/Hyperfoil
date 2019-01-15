package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.ServiceLoadedBuilder;
import io.hyperfoil.api.connection.Request;

public interface StatusExtractor extends Serializable {
   void setStatus(Request request, int status);

   interface BuilderFactory extends ServiceLoadedBuilder.Factory<StatusExtractor> {}
}

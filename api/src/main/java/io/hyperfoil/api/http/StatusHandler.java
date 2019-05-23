package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.ServiceLoadedFactory;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.function.SerializableSupplier;

public interface StatusHandler extends Serializable {
   void handleStatus(Request request, int status);

   interface Builder extends BuilderBase<Builder> {
      StatusHandler build(SerializableSupplier<? extends Step> step);
   }
   interface BuilderFactory extends ServiceLoadedFactory<StatusHandler.Builder> {}
}

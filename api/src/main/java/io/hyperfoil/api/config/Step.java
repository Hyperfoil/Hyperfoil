package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.List;

import io.hyperfoil.api.session.Session;

public interface Step extends Serializable {
   boolean invoke(Session session);

   interface BuilderFactory extends ServiceLoadedBuilder.Factory<List<Step>> {}
}

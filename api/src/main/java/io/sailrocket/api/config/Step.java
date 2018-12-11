package io.sailrocket.api.config;

import java.io.Serializable;
import java.util.List;

import io.sailrocket.api.session.Session;

public interface Step extends Serializable {
   boolean invoke(Session session);

   interface BuilderFactory extends ServiceLoadedBuilder.Factory<List<Step>> {}
}

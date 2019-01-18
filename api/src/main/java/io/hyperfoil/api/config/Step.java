package io.hyperfoil.api.config;

import java.io.Serializable;

import io.hyperfoil.api.session.Session;

public interface Step extends Serializable {
   boolean invoke(Session session);

}

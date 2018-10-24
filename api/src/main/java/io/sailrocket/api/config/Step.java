package io.sailrocket.api.config;

import java.io.Serializable;

import io.sailrocket.api.session.Session;

public interface Step extends Serializable {
   boolean invoke(Session session);
}

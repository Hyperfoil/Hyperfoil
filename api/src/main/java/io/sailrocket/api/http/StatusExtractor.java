package io.sailrocket.api.http;

import java.io.Serializable;

import io.sailrocket.api.session.Session;

public interface StatusExtractor extends Serializable {
   void setStatus(int status, Session session);
}

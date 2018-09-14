package io.sailrocket.api;

import java.io.Serializable;

public interface StatusExtractor extends Serializable {
   void setStatus(int status, Session session);
}

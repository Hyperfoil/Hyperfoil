package io.sailrocket.core.machine;

public interface Action {
   State invoke(Session session);
}

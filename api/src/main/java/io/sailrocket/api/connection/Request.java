package io.sailrocket.api.connection;

public interface Request {
   Connection connection();

   boolean isCompleted();

   void setCompleted();
}

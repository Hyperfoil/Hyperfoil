package io.sailrocket.api;

public interface RequestQueue {
   Request prepare();

   Request peek();

   Request complete();

   boolean isFull();

   class Request {
      public long startTime;
      public SequenceInstance sequence;
   }
}

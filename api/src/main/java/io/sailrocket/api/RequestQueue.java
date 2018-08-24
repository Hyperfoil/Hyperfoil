package io.sailrocket.api;

public interface RequestQueue {
   Request prepare();

   Request peek();

   Request complete();

   class Request {
      public long startTime;
      public SequenceInstance sequence;
   }
}

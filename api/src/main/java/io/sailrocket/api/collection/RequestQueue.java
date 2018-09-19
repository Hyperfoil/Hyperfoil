package io.sailrocket.api.collection;

import io.sailrocket.api.session.SequenceInstance;

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

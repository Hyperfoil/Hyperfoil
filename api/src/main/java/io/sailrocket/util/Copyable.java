package io.sailrocket.util;

public interface Copyable<T extends Copyable> {
   /**
    * @return Deep copy of this object
    */
   T copy();
}

package io.hyperfoil.api.statistics;

import java.io.Serializable;

public interface CustomValue extends Serializable, Cloneable {
   void add(CustomValue other);
   void substract(CustomValue other);
   void reset();
   CustomValue clone();
}

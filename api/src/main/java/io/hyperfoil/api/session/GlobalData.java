package io.hyperfoil.api.session;

import java.io.Serializable;

public interface GlobalData {
   /**
    * Offers an element for publishing. When the publishing phase terminates all elements with the same key
    * are combined together (in arbitrary order) on the controller. The resulting element is then
    * made available to phases started strictly after publishing phase. If there is already an existing
    * element with this key present all remaining phases are cancelled and an error is reported.
    *
    * @param phase
    * @param key Identifier.
    * @param element Custom element.
    */
   void publish(String phase, String key, Element element);

   /**
    * Retrieves the element created in one of strictly preceding phases.
    *
    * @param key Identifier.
    * @return Custom element.
    */
   Element read(String key);

   /**
    * This interface is typically implemented in extensions.
    */
   interface Element extends Serializable {
      /**
       * Creates a new instance of accumulator for combining multiple elements.
       *
       * @return Empty accumulator instance.
       */
      Accumulator newAccumulator();
   }

   interface Accumulator {
      /**
       * Add a new element to the accumulator. The elements can be combined in arbitrary
       * order, extracted into another element using {@link #complete()} and combined again.
       *
       * @param e Element of the same type that created this accumulator.
       * @throws IllegalArgumentException if the element is of unsupported type.
       */
      void add(Element e);

      /**
       * Transforms contents of this accumulator into a combined element.
       *
       * @return Combined element.
       */
      Element complete();
   }
}

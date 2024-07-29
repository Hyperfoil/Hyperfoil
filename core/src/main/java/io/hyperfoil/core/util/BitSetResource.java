package io.hyperfoil.core.util;

import java.util.BitSet;

import io.hyperfoil.api.session.Session;

public interface BitSetResource extends Session.Resource {

   void set(int index);

   boolean get(int index);

   void clear(int index);

   static BitSetResource with(int nbits) {
      if (nbits == 0 || nbits == 1) {
         class SingleBitSetResource implements BitSetResource {

            private boolean set;

            @Override
            public void set(int index) {
               validateBitIndex(index);
               set = true;
            }

            private static void validateBitIndex(int index) {
               if (index != 0) {
                  throw new IndexOutOfBoundsException();
               }
            }

            @Override
            public boolean get(int index) {
               validateBitIndex(index);
               return set;
            }

            @Override
            public void clear(int index) {
               validateBitIndex(index);
               set = false;
            }
         }

         return new SingleBitSetResource();
      }
      class MultiBitSetResource extends BitSet implements BitSetResource {

         private final int nBits;

         MultiBitSetResource(int nbits) {
            super(nbits);
            this.nBits = nbits;
         }

         @Override
         public void set(final int bitIndex) {
            validateBitIndex(bitIndex);
            super.set(bitIndex);
         }

         private void validateBitIndex(int bitIndex) {
            if (bitIndex >= nBits) {
               throw new IndexOutOfBoundsException();
            }
         }

         @Override
         public boolean get(final int bitIndex) {
            validateBitIndex(bitIndex);
            return super.get(bitIndex);
         }

         @Override
         public void clear(final int bitIndex) {
            validateBitIndex(bitIndex);
            super.clear(bitIndex);
         }
      }

      return new MultiBitSetResource(nbits);
   }

}

package io.hyperfoil.core.handlers;

import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

class BaseSearchContext implements Session.Resource {
   private static final int MAX_PARTS = 16;
   ByteBuf[] parts = new ByteBuf[MAX_PARTS];
   int[] startIndices = new int[MAX_PARTS];
   int[] endIndices = new int[MAX_PARTS];
   int currentPart = -1;
   int hashedBytes;
   int currentHash;

   static int computeHash(byte[] bytes) {
      int hash = 0;
      for (int b : bytes) {
         hash = 31 * hash + b;
      }
      return hash;
   }

   static int computeCoef(int length) {
      int value = 1;
      for (int i = length; i > 0; --i) {
         value *= 31;
      }
      return value;
   }

   void add(ByteBuf data, int offset, int length) {
      ++currentPart;
      if (currentPart >= parts.length) {
         shiftParts();
      }
      parts[currentPart] = data.retain();
      startIndices[currentPart] = offset;
      endIndices[currentPart] = offset + length;
   }

   int initHash(int index, int lookupLength) {
      ByteBuf data = parts[currentPart];
      while (index < endIndices[currentPart] && hashedBytes < lookupLength) {
         currentHash = 31 * currentHash + data.getByte(index++);
         ++hashedBytes;
      }
      return index;
   }

   void shiftParts() {
      parts[0].release();
      System.arraycopy(parts, 1, parts, 0, parts.length - 1);
      System.arraycopy(startIndices, 1, startIndices, 0, startIndices.length - 1);
      System.arraycopy(endIndices, 1, endIndices, 0, endIndices.length - 1);
      --currentPart;
   }

   int byteRelative(int currentIndex, int numBytesBack) {
      int part = currentPart;
      int currentBytesRead = currentIndex - startIndices[part];
      if (numBytesBack <= currentBytesRead) {
         return parts[part].getByte(currentIndex - numBytesBack);
      } else {
         numBytesBack -= currentBytesRead;
         --part;
      }
      while (numBytesBack > endIndices[part] - startIndices[part]) {
         numBytesBack -= endIndices[part] - startIndices[part];
         --part;
      }
      return parts[part].getByte(endIndices[part] - numBytesBack);
   }

   void reset() {
      hashedBytes = 0;
      currentHash = 0;
      currentPart = -1;
      for (int i = 0; i < parts.length; ++i) {
         if (parts[i] != null) {
            parts[i].release();
            parts[i] = null;
         }
      }
   }

   void advance(byte c, int coef, int currentIndex, int numBytesBack) {
      currentHash = 31 * currentHash + c - coef * byteRelative(currentIndex, numBytesBack);
   }
}

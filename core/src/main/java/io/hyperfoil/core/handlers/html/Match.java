package io.hyperfoil.core.handlers.html;

import io.hyperfoil.core.util.Util;
import io.netty.buffer.ByteBuf;

class Match {
   private int partial = 0;
   private boolean hasMatch = false;

   public void shift(ByteBuf data, int offset, int length, boolean isLast, byte[] string) {
      if (partial < 0) {
         if (isLast) {
            partial = 0;
         }
         return;
      }
      int i = 0;
      for (; i < length && partial < string.length; ++i, ++partial) {
         if (string[partial] != Util.toLowerCase(data.getByte(offset + i))) {
            hasMatch = false;
            if (!isLast) {
               partial = -1;
            } else {
               partial = 0;
            }
            return;
         }
      }
      if (isLast) {
         hasMatch = i == length && partial == string.length;
         partial = 0;
      }
   }

   public void reset() {
      hasMatch = false;
      partial = 0;
   }

   public boolean hasMatch() {
      return hasMatch;
   }
}

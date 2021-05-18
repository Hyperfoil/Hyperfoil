package io.hyperfoil.api.session;

import java.util.ArrayList;

import io.hyperfoil.impl.CollectingVisitor;

public class AccessVisitor extends CollectingVisitor<ReadAccess> {
   private final ArrayList<ReadAccess> reads = new ArrayList<>();
   private final ArrayList<WriteAccess> writes = new ArrayList<>();

   public AccessVisitor() {
      super(ReadAccess.class);
   }

   @Override
   protected boolean process(ReadAccess value) {
      reads.add(value);
      if (value instanceof WriteAccess) {
         writes.add((WriteAccess) value);
      }
      return false;
   }

   public ReadAccess[] reads() {
      return reads.toArray(new ReadAccess[0]);
   }

   public WriteAccess[] writes() {
      return writes.toArray(new WriteAccess[0]);
   }
}

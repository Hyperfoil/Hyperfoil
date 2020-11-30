package io.hyperfoil.core.util;

import java.io.Serializable;

public final class Unique implements Serializable {
   private final boolean sequenceScoped;

   public Unique() {
      this(false);
   }

   public Unique(boolean sequenceScoped) {
      this.sequenceScoped = sequenceScoped;
   }

   public boolean isSequenceScoped() {
      return sequenceScoped;
   }

   @Override
   public String toString() {
      return String.format("%s@%08x", (sequenceScoped ? "<unique[]>" : "<unique>"), System.identityHashCode(this));
   }
}

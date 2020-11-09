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
}

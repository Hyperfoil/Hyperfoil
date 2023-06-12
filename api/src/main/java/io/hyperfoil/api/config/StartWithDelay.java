package io.hyperfoil.api.config;

import java.io.Serializable;

public class StartWithDelay implements Serializable {
   public final String phase;
   public final long delay;

   public StartWithDelay(String phase, long delay) {
      this.phase = phase;
      this.delay = delay;
   }
}

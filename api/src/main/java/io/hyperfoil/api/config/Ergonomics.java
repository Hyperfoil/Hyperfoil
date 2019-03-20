package io.hyperfoil.api.config;

import java.io.Serializable;

/**
 * Automatic options that insert or adjust steps or sequences to provide 'common' behaviour.
 */
public class Ergonomics implements Serializable {
   private final boolean repeatCookies;

   public Ergonomics(boolean repeatCookies) {
      this.repeatCookies = repeatCookies;
   }

   public boolean repeatCookies() {
      return repeatCookies;
   }
}

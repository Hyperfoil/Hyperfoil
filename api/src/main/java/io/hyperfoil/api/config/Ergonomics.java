package io.hyperfoil.api.config;

import java.io.Serializable;

/**
 * Automatic options that insert or adjust steps or sequences to provide 'common' behaviour.
 */
public class Ergonomics implements Serializable {
   private final boolean repeatCookies;
   private final boolean userAgentFromSession;

   public Ergonomics(boolean repeatCookies, boolean userAgentFromSession) {
      this.repeatCookies = repeatCookies;
      this.userAgentFromSession = userAgentFromSession;
   }

   public boolean repeatCookies() {
      return repeatCookies;
   }

   public boolean userAgentFromSession() {
      return userAgentFromSession;
   }
}

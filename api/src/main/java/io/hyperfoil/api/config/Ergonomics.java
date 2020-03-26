package io.hyperfoil.api.config;

import java.io.Serializable;

/**
 * Automatic options that insert or adjust steps or sequences to provide 'common' behaviour.
 */
public class Ergonomics implements Serializable {
   private final boolean repeatCookies;
   private final boolean userAgentFromSession;
   private final boolean privateHttpPools;
   private final boolean autoRangeCheck;
   private final boolean stopOnInvalid;

   public Ergonomics(boolean repeatCookies, boolean userAgentFromSession, boolean privateHttpPools, boolean autoRangeCheck, boolean stopOnInvalid) {
      this.repeatCookies = repeatCookies;
      this.userAgentFromSession = userAgentFromSession;
      this.privateHttpPools = privateHttpPools;
      this.autoRangeCheck = autoRangeCheck;
      this.stopOnInvalid = stopOnInvalid;
   }

   public boolean repeatCookies() {
      return repeatCookies;
   }

   public boolean userAgentFromSession() {
      return userAgentFromSession;
   }

   public boolean privateHttpPools() {
      return privateHttpPools;
   }

   public boolean autoRangeCheck() {
      return autoRangeCheck;
   }

   public boolean stopOnInvalid() {
      return stopOnInvalid;
   }
}

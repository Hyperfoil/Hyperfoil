package io.hyperfoil.api.config;

public class ErgonomicsBuilder {
   private boolean repeatCookies = true;
   private boolean userAgentFromSession = true;
   private boolean privateHttpPools = false;

   /**
    * Set global cookie-repeating behaviour for all steps.
    *
    * @param repeatCookies Auto repeat?
    * @return Self.
    */
   public ErgonomicsBuilder repeatCookies(boolean repeatCookies) {
      this.repeatCookies = repeatCookies;
      return this;
   }

   public boolean repeatCookies() {
      return repeatCookies;
   }

   public ErgonomicsBuilder userAgentFromSession(boolean userAgentFromSession) {
      this.userAgentFromSession = userAgentFromSession;
      return this;
   }

   public boolean userAgentFromSession() {
      return userAgentFromSession;
   }

   public ErgonomicsBuilder privateHttpPools(boolean privateHttpPools) {
      this.privateHttpPools = privateHttpPools;
      return this;
   }

   public Ergonomics build() {
      return new Ergonomics(repeatCookies, userAgentFromSession, privateHttpPools);
   }
}

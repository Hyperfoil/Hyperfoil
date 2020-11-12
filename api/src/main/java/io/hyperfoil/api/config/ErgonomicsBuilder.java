package io.hyperfoil.api.config;

import io.hyperfoil.api.http.FollowRedirect;

public class ErgonomicsBuilder {
   private final BenchmarkBuilder parent;
   private boolean repeatCookies = true;
   private boolean userAgentFromSession = true;
   private boolean privateHttpPools = false;
   private boolean autoRangeCheck = true;
   private boolean stopOnInvalid = true;
   private FollowRedirect followRedirect = FollowRedirect.NEVER;

   public ErgonomicsBuilder(BenchmarkBuilder parent) {
      this.parent = parent;
   }

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

   public boolean autoRangeCheck() {
      return autoRangeCheck;
   }

   public ErgonomicsBuilder autoRangeCheck(boolean autoRangeCheck) {
      this.autoRangeCheck = autoRangeCheck;
      return this;
   }

   public boolean stopOnInvalid() {
      return stopOnInvalid;
   }

   public ErgonomicsBuilder stopOnInvalid(boolean stopOnInvalid) {
      this.stopOnInvalid = stopOnInvalid;
      return this;
   }

   public FollowRedirect followRedirect() {
      return followRedirect;
   }

   public ErgonomicsBuilder followRedirect(FollowRedirect followRedirect) {
      this.followRedirect = followRedirect;
      return this;
   }

   public BenchmarkBuilder endErgonomics() {
      return parent;
   }

   public Ergonomics build() {
      return new Ergonomics(repeatCookies, userAgentFromSession, privateHttpPools, autoRangeCheck, stopOnInvalid, followRedirect);
   }
}

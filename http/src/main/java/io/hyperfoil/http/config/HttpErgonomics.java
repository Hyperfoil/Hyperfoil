package io.hyperfoil.http.config;

import io.hyperfoil.http.api.FollowRedirect;

// Contrary to the builder - immutable instance model we're using for most configuration objects
// we'll keep only single object for ergonomics as this is used only when the benchmark is being built.
public class HttpErgonomics {
   private final HttpPluginBuilder parent;
   private boolean repeatCookies = true;
   private boolean userAgentFromSession = true;
   private boolean autoRangeCheck = true;
   private boolean stopOnInvalid = true;
   private FollowRedirect followRedirect = FollowRedirect.NEVER;
   private boolean compensateInternalLatency = false;

   public HttpErgonomics(HttpPluginBuilder parent) {
      this.parent = parent;
   }

   /**
    * Set global cookie-repeating behaviour for all steps.
    *
    * @param repeatCookies Auto repeat?
    * @return Self.
    */
   public HttpErgonomics repeatCookies(boolean repeatCookies) {
      this.repeatCookies = repeatCookies;
      return this;
   }

   public boolean repeatCookies() {
      return repeatCookies;
   }

   public HttpErgonomics userAgentFromSession(boolean userAgentFromSession) {
      this.userAgentFromSession = userAgentFromSession;
      return this;
   }

   public boolean userAgentFromSession() {
      return userAgentFromSession;
   }

   public boolean autoRangeCheck() {
      return autoRangeCheck;
   }

   public HttpErgonomics autoRangeCheck(boolean autoRangeCheck) {
      this.autoRangeCheck = autoRangeCheck;
      return this;
   }

   public boolean stopOnInvalid() {
      return stopOnInvalid;
   }

   public HttpErgonomics stopOnInvalid(boolean stopOnInvalid) {
      this.stopOnInvalid = stopOnInvalid;
      return this;
   }

   public FollowRedirect followRedirect() {
      return followRedirect;
   }

   public HttpErgonomics followRedirect(FollowRedirect followRedirect) {
      this.followRedirect = followRedirect;
      return this;
   }

   public boolean compensateInternalLatency() {
      return compensateInternalLatency;
   }

   /**
    * When enabled, the first HTTP request in a root sequence of an open model phase will use the session's
    * intended (scheduled) start time as the request start timestamp, compensating for internal coordinated omission.
    *
    * @param compensateInternalLatency Enable internal latency compensation?
    * @return Self.
    */
   public HttpErgonomics compensateInternalLatency(boolean compensateInternalLatency) {
      this.compensateInternalLatency = compensateInternalLatency;
      return this;
   }

   public HttpPluginBuilder endErgonomics() {
      return parent;
   }
}

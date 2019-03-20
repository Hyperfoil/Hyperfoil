package io.hyperfoil.api.config;

public class ErgonomicsBuilder {
   private boolean repeatCookies = true;

   /**
    * Set global cookie-repeating behaviour for all steps.
    */
   public ErgonomicsBuilder repeatCookies(boolean repeatCookies) {
      this.repeatCookies = repeatCookies;
      return this;
   }

   public boolean repeatCookies() {
      return repeatCookies;
   }

   public Ergonomics build() {
      return new Ergonomics(repeatCookies);
   }
}

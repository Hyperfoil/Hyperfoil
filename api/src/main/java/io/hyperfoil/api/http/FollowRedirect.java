package io.hyperfoil.api.http;

public enum FollowRedirect {
   /**
    * Do not insert any automatic redirection handling.
    */
   NEVER,
   /**
    * Redirect only upon status 3xx accompanied with a 'location' header.
    * Status, headers, body and completions handlers are suppressed in this case
    * (only raw-bytes handlers are still running).
    * This is the default option.
    */
   LOCATION_ONLY,
   /**
    * Handle only HTML response with META refresh header.
    * All handlers are executed as usual.
    */
   HTML_ONLY,
   /**
    * Implement both status 3xx + location and HTML redirects.
    */
   ALWAYS,
}

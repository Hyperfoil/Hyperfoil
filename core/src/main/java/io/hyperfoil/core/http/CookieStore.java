package io.hyperfoil.core.http;

import io.hyperfoil.api.session.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CookieStore implements Session.Resource {
   private static final Logger log = LoggerFactory.getLogger(CookieRecorder.class);

   // We need only single object for all cookies
   public static final Session.ResourceKey<CookieStore> COOKIES = new Session.ResourceKey<CookieStore>() {};

   private static final int MAX_SITES = 16;
   private final String[] sites = new String[MAX_SITES];
   private final CharSequence[] cookies = new CharSequence[MAX_SITES];

   public void setCookie(String site, CharSequence cookie) {
      for (int i = 0; i < sites.length; ++i) {
         if (sites[i] == null) {
            sites[i] = site;
            cookies[i] = cookie;
            return;
         } else if (site.equals(sites[i])) {
            cookies[i] = cookie;
            return;
         }
      }
      log.error("Exceeded number of cookies, dropping one for {}: {}", site, cookie);
   }

   public CharSequence getCookie(String site) {
      for (int i = 0; i < sites.length; ++i) {
         if (sites[i] == null) {
            return null;
         } else if (sites[i].equals(site)) {
            return cookies[i];
         }
      }
      return null;
   }
}

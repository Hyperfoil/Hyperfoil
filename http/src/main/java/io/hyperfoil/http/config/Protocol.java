package io.hyperfoil.http.config;

import java.util.stream.Stream;

public enum Protocol {
   HTTP("http", 80, false),
   HTTPS("https", 443, true);

   public final String scheme;
   public final int defaultPort;
   public final boolean secure;

   Protocol(String scheme, int defaultPort, boolean secure) {
      this.scheme = scheme;
      this.defaultPort = defaultPort;
      this.secure = secure;
   }

   public static Protocol fromScheme(String scheme) {
      return Stream.of(values()).filter(p -> p.scheme.equals(scheme)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown scheme '" + scheme + "'"));
   }

   public static Protocol fromPort(int port) {
      if (port == HTTPS.defaultPort) {
         return HTTPS;
      } else {
         return HTTP;
      }
   }

   public int portOrDefault(int port) {
      return port < 0 ? defaultPort : port;
   }

   public boolean secure() {
      return secure;
   }

   @Override
   public String toString() {
      return secure ?  "https://" : "http://";
   }
}

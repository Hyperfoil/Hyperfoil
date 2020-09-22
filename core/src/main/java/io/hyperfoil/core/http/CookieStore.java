package io.hyperfoil.core.http;

import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.util.Util;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class CookieStore implements Session.Resource {
   private static final Logger log = LoggerFactory.getLogger(CookieRecorder.class);

   // We need only single object for all cookies
   public static final Session.ResourceKey<CookieStore> COOKIES = new Session.ResourceKey<CookieStore>() {};

   private static final Attribute[] ATTRIBUTES = Attribute.values();
   private static final int MAX_SITES = 16;
   private final Cookie[] cookies = new Cookie[MAX_SITES];

   public CookieStore() {
      for (int i = 0; i < cookies.length; ++i) {
         cookies[i] = new Cookie();
      }
   }

   @Override
   public void onSessionReset() {
      for (int i = 0; i < cookies.length; ++i) {
         cookies[i].clear();
      }
   }

   public void setCookie(CharSequence requestOrigin, CharSequence requestPath, CharSequence seq) {
      int nameEnd = HttpUtil.indexOf(seq, 0, '=');
      if (nameEnd < 0) {
         log.warn("Invalid cookie value (no name): {}", seq);
         return;
      }
      CharSequence name = seq.subSequence(0, nameEnd);
      int valueEnd = HttpUtil.indexOf(seq, nameEnd + 1, ';');
      CharSequence nameValue = seq.subSequence(0, valueEnd);
      CharSequence domain = null;
      CharSequence path = null;
      boolean secure = false;
      long maxAge = Long.MAX_VALUE;
      long expires = Long.MAX_VALUE;
      ++valueEnd;
      while (valueEnd < seq.length()) {
         for (; valueEnd < seq.length() && seq.charAt(valueEnd) == ' '; ++valueEnd) ;
         int semIndex = HttpUtil.indexOf(seq, valueEnd, ';');
         for (int a = 0; a < ATTRIBUTES.length; ++a) {
            Attribute attribute = ATTRIBUTES[a];
            if (matchPrefix(attribute.text, seq, valueEnd)) {
               switch (attribute) {
                  case EXPIRES:
                     expires = HttpUtil.parseDate(seq, valueEnd + attribute.text.length(), semIndex);
                     break;
                  case MAX_AGE:
                     maxAge = Util.parseLong(seq, valueEnd + attribute.text.length(), semIndex, Long.MAX_VALUE);
                     break;
                  case DOMAIN:
                     // ignore leading dot
                     if (valueEnd < seq.length() && seq.charAt(valueEnd) == '.') {
                        ++valueEnd;
                     }
                     domain = seq.subSequence(valueEnd + attribute.text.length(), semIndex);
                     break;
                  case PATH:
                     path = seq.subSequence(valueEnd + attribute.text.length(), semIndex);
                     break;
                  case SECURE:
                     secure = true;
                     break;
                  case HTTPONLY:
                  case EXTENSION:
                  default:
                     // silently ignored
                     break;
               }
               break;
            }
         }
         valueEnd = semIndex + 1;
      }
      // omitted Domain attribute means that the cookie should be returned only to origin
      boolean exactDomain = false;
      // We can set cookie for domain or superdomain of request origin
      if (domain == null) {
         domain = requestOrigin;
         exactDomain = true;
      } else if (!isSubdomain(requestOrigin, domain)) {
         log.trace("Refusing to store cookie for domain {}, origin is {}", domain, requestOrigin);
         return;
      }
      int requestPathLastSlashIndex = HttpUtil.lastIndexOf(requestPath, requestPath.length(), '/');
      if (path == null) {
         path = requestPath.subSequence(0, requestPathLastSlashIndex + 1);
      } else if (!isSubpath(requestPath, requestPathLastSlashIndex + 1, path, path.length())) {
         log.trace("Refusing to store cookie for path {}, origin is {}", path, requestPath);
         return;
      }
      long now = System.currentTimeMillis();
      if (maxAge != Long.MAX_VALUE) {
         expires = now + maxAge * 1000;
      }
      for (int i = 0; i < cookies.length; ++i) {
         if (cookies[i].name == null || cookies[i].name.length() == 0 || (
               AsciiString.contentEquals(cookies[i].name, name) &&
                     AsciiString.contentEquals(cookies[i].domain, domain) &&
                     AsciiString.contentEquals(cookies[i].path, path))) {
            if (nameValue.length() == valueEnd + 1 || expires <= now) {
               cookies[i].name = ""; // invalidate this entry as it's expired
            } else {
               cookies[i].name = name;
               cookies[i].nameValue = nameValue;
               cookies[i].domain = domain;
               cookies[i].exactDomain = exactDomain;
               cookies[i].path = path;
               cookies[i].secure = secure;
               cookies[i].expires = expires;
            }
            return;
         }
      }
      log.error("Exceeded number of cookies, dropping: {}", seq);
   }

   private static boolean isSubpath(CharSequence subpath, int subpathLength, CharSequence path, int pathLength) {
      // example: subpath = /foo/bar, path = /foo -> true
      if (pathLength > subpathLength) {
         return false;
      }
      return AsciiString.regionMatches(subpath, false, 0, path, 0, pathLength);
   }

   private static boolean isSubdomain(CharSequence subdomain, CharSequence domain) {
      if (subdomain.length() < domain.length()) {
         return false;
      }
      return AsciiString.regionMatches(subdomain, false, subdomain.length() - domain.length(), domain, 0, domain.length());
   }

   private static boolean matchPrefix(CharSequence prefix, CharSequence seq, int begin) {
      int maxLength = prefix.length();
      if (maxLength > seq.length() - begin) {
         return false;
      }
      for (int i = 0; i < maxLength; ++i) {
         if (prefix.charAt(i) != Character.toLowerCase(seq.charAt(begin + i))) {
            return false;
         }
      }
      return true;
   }

   public void appendCookies(HttpRequestWriter requestWriter) {
      CharSequence domain = requestWriter.connection().host();
      CharSequence path = requestWriter.request().path;
      long now = System.currentTimeMillis();
      for (int i = 0; i < cookies.length; ++i) {
         Cookie c = cookies[i];
         if (c.name == null) {
            break;
         } else if (c.name.length() == 0) {
            // continue
         } else if (((!c.exactDomain && isSubdomain(domain, c.domain)) || AsciiString.contentEquals(domain, c.domain)) &&
               isSubpath(path, path.length(), c.path, c.path.length()) &&
               (!c.secure || requestWriter.connection().isSecure())) {
            if (now >= c.expires) {
               c.name = "";
            } else {
               requestWriter.putHeader(HttpHeaderNames.COOKIE, c.nameValue);
            }
         }
      }
   }

   static class Cookie {
      CharSequence name;
      CharSequence nameValue;
      CharSequence domain;
      boolean exactDomain;
      CharSequence path;
      long expires;
      boolean secure;

      public void clear() {
         name = null;
         nameValue = null;
         domain = null;
         exactDomain = false;
         path = null;
         expires = 0;
         secure = false;
      }
   }

   private enum Attribute {
      EXPIRES("expires="), MAX_AGE("max-age="), DOMAIN("domain="), PATH("path="), SECURE("secure"), HTTPONLY("httponly"), EXTENSION("");

      final CharSequence text;

      Attribute(CharSequence text) {
         this.text = text;
      }
   }
}

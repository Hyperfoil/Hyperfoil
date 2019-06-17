package io.hyperfoil.core.http;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimeZone;

import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.util.Util;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import sun.util.calendar.ZoneInfo;

class CookieStore implements Session.Resource {
   private static final Logger log = LoggerFactory.getLogger(CookieRecorder.class);
   private static final boolean trace = log.isTraceEnabled();

   // We need only single object for all cookies
   public static final Session.ResourceKey<CookieStore> COOKIES = new Session.ResourceKey<CookieStore>() {};

   private static final Attribute[] ATTRIBUTES = Attribute.values();
   private static final int MAX_SITES = 16;
   private static final CharSequence[] MONTHS = { "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec" };
   private static final TimeZone UTC = ZoneInfo.getTimeZone(ZoneId.of("UTC"));
   private final Cookie[] cookies = new Cookie[MAX_SITES];


   public CookieStore() {
      for (int i = 0; i < cookies.length; ++i) {
         cookies[i] = new Cookie();
      }
   }

   public void setCookie(CharSequence requestOrigin, CharSequence requestPath, CharSequence seq) {
      int nameEnd = indexOf(seq, 0, '=');
      if (nameEnd < 0) {
         log.warn("Invalid cookie value (no name): {}", seq);
         return;
      }
      CharSequence name = seq.subSequence(0, nameEnd);
      int valueEnd = indexOf(seq, nameEnd + 1, ';');
      CharSequence nameValue = seq.subSequence(0, valueEnd);
      CharSequence domain = null;
      CharSequence path = null;
      boolean secure = false;
      long maxAge = Long.MAX_VALUE;
      long expires = Long.MAX_VALUE;
      ++valueEnd;
      while (valueEnd < seq.length()) {
         for ( ; valueEnd < seq.length() && seq.charAt(valueEnd) == ' '; ++valueEnd);
         int semIndex = indexOf(seq, valueEnd, ';');
         for (int a = 0; a < ATTRIBUTES.length; ++a) {
            Attribute attribute = ATTRIBUTES[a];
            if (matchPrefix(attribute.text, seq, valueEnd)) {
               switch (attribute) {
                  case EXPIRES:
                     expires = parseDate(seq, valueEnd + attribute.text.length(), semIndex);
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
      int requestPathLastSlashIndex = lastIndexOf(requestPath, requestPath.length(), '/');
      if (path == null) {
         path = requestPath.subSequence(0, requestPathLastSlashIndex + 1);
      } else if (!isSubpath(requestPath, requestPathLastSlashIndex, path, path.length())) {
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

   private static int indexOf(CharSequence seq, int begin, char c) {
      int length = seq.length();
      for (int i = begin; i < length; ++i) {
         if (seq.charAt(i) == c) {
            return i;
         }
      }
      return length;
   }

   private static int lastIndexOf(CharSequence seq, int end, char c) {
      for (int i = end - 1; i >= 0; --i) {
         if (seq.charAt(i) == c) {
            return i;
         }
      }
      return -1;
   }

   private static long parseDate(CharSequence seq, int begin, int end) {
      int i = begin;
      for (; i < end && seq.charAt(i) != ','; ++i) ; // skip day-of-week
      ++i; // skip the comma
      for (; i < end && seq.charAt(i) == ' '; ++i) ; // skip spaces
      if (i + 2 >= end) {
         log.warn("Cannot parse date {}", seq.subSequence(begin, end));
         return 0;
      }
      int dayOfMonth = twoDigits(seq, i);
      if (dayOfMonth < 1 || dayOfMonth > 31) {
         log.warn("Cannot parse date {}", seq.subSequence(begin, end));
         return 0;
      }
      i += 3; // two digits and '-'
      if (i + 3 >= end) {
         log.warn("Cannot parse date {}", seq.subSequence(begin, end));
         return 0;
      }
      int month = 0;
      for (int m = 0; m < MONTHS.length; ++m) {
         if (Character.toLowerCase(seq.charAt(i)) == MONTHS[m].charAt(0) &&
               Character.toLowerCase(seq.charAt(i + 1)) == MONTHS[m].charAt(1) &&
               Character.toLowerCase(seq.charAt(i + 2)) == MONTHS[m].charAt(2)) {
            month = m + 1;
            break;
         }
      }
      if (month == 0) {
         log.warn("Cannot parse month in date {}", seq.subSequence(begin, end));
         return 0;
      }
      i+= 4; // skip month and '-'
      int nextSpace = indexOf(seq, i, ' ');
      int year;
      if (nextSpace - i == 4) {
         year = (int) Util.parseLong(seq, i, nextSpace);
         if (year < 1600 || year >= 3000) {
            log.warn("Cannot parse year in date {}", seq.subSequence(begin, end));
            return 0;
         }
      } else if (nextSpace - i == 2) {
         year = twoDigits(seq, i);
         if (year < 0 || year > 100) {
            log.warn("Cannot parse year in date {}", seq.subSequence(begin, end));
            return 0;
         }
         if (year < 70) {
            year += 2000;
         } else {
            year += 1900;
         }
      } else {
         log.warn("Cannot parse year in date {}", seq.subSequence(begin, end));
         return 0;
      }
      for (i = nextSpace + 1; i < end && seq.charAt(i) == ' '; ++i); // skip spaces

      if (i + 8 >= end || seq.charAt(i + 2) != ':' || seq.charAt(i + 5) != ':') {
         log.warn("Cannot parse time in date {}", seq.subSequence(begin, end));
         return 0;
      }
      int hour = twoDigits(seq, i);
      int minute = twoDigits(seq, i + 3);
      int second = twoDigits(seq, i + 6);
      if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
         log.warn("Cannot parse time in date {}", seq.subSequence(begin, end));
         return 0;
      }
      for (i += 8; i < end && seq.charAt(i) == ' '; ++i); // skip spaces

      TimeZone timeZone = UTC;
      if (i < end) {
         timeZone = ZoneInfo.getTimeZone(ZoneId.of(seq.subSequence(i, end).toString()));
      }
      // TODO: calculate epoch millis without allocation
      Calendar calendar = Calendar.getInstance(timeZone);
      calendar.set(year, month, dayOfMonth, hour, minute, second);
      return calendar.getTimeInMillis();
   }

   private static int twoDigits(CharSequence seq, int i) {
      return 10 * (seq.charAt(i) - '0') + (seq.charAt(i) - '0');
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
   }

   private enum Attribute {
      EXPIRES("expires="), MAX_AGE("max-age="), DOMAIN("domain="), PATH("path="), SECURE("secure"), HTTPONLY("httponly"), EXTENSION("");

      final CharSequence text;

      Attribute(CharSequence text) {
         this.text = text;
      }
   }
}

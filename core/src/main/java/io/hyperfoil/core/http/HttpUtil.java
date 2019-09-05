package io.hyperfoil.core.http;

import java.util.Calendar;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hyperfoil.util.Util;
import io.netty.util.AsciiString;

public final class HttpUtil {
   private static final Logger log = LoggerFactory.getLogger(HttpUtil.class);
   private static final CharSequence[] MONTHS = { "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec" };
   private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
   private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

   public static final String HTTP_PREFIX = "http://";
   public static final String HTTPS_PREFIX = "https://";

   private HttpUtil() {}

   static int indexOf(CharSequence seq, int begin, char c) {
      int length = seq.length();
      for (int i = begin; i < length; ++i) {
         if (seq.charAt(i) == c) {
            return i;
         }
      }
      return length;
   }

   static int lastIndexOf(CharSequence seq, int end, char c) {
      for (int i = end - 1; i >= 0; --i) {
         if (seq.charAt(i) == c) {
            return i;
         }
      }
      return -1;
   }

   static long parseDate(CharSequence seq) {
      return parseDate(seq, 0, seq.length());
   }

   static long parseDate(CharSequence seq, int begin, int end) {
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
      i += 4; // skip month and '-'
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
         if (end - i >= 3 && AsciiString.regionMatches(seq, false, i, "GMT", 0, 3)) {
            timeZone = GMT;
         } else if (end - i >= 3 && AsciiString.regionMatches(seq, false, i, "UTC", 0, 3)) {
            timeZone = UTC;
         } else {
            timeZone = TimeZone.getTimeZone(seq.subSequence(i, end).toString());
         }
      }
      // TODO: calculate epoch millis without allocation
      Calendar calendar = Calendar.getInstance(timeZone);
      calendar.set(year, month, dayOfMonth, hour, minute, second);
      return calendar.getTimeInMillis();
   }

   private static int twoDigits(CharSequence seq, int i) {
      return 10 * (seq.charAt(i) - '0') + (seq.charAt(i + 1) - '0');
   }

   public static CharSequence formatDate(long timestamp) {
      Calendar calendar = Calendar.getInstance(GMT);
      calendar.setTimeInMillis(timestamp);
      byte[] bytes = new byte[29];
      switch (calendar.get(Calendar.DAY_OF_WEEK)) {
         case Calendar.SUNDAY:
            bytes[0] = 'S'; bytes[1] = 'u'; bytes[2] = 'n';
            break;
         case Calendar.MONDAY:
            bytes[0] = 'M'; bytes[1] = 'o'; bytes[2] = 'n';
            break;
         case Calendar.TUESDAY:
            bytes[0] = 'T'; bytes[1] = 'u'; bytes[2] = 'e';
            break;
         case Calendar.WEDNESDAY:
            bytes[0] = 'W'; bytes[1] = 'e'; bytes[2] = 'd';
            break;
         case Calendar.THURSDAY:
            bytes[0] = 'T'; bytes[1] = 'h'; bytes[2] = 'u';
            break;
         case Calendar.FRIDAY:
            bytes[0] = 'F'; bytes[1] = 'r'; bytes[2] = 'i';
            break;
         case Calendar.SATURDAY:
            bytes[0] = 'S'; bytes[1] = 'a'; bytes[2] = 't';
            break;
      }
      bytes[3] = ','; bytes[4] = ' ';
      int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      bytes[5] = (byte) ('0' + dayOfMonth / 10);
      bytes[6] = (byte) ('0' + dayOfMonth % 10);
      bytes[7] = '-';
      CharSequence month = MONTHS[calendar.get(Calendar.MONTH) - 1];
      bytes[8] = (byte) month.charAt(0);
      bytes[9] = (byte) month.charAt(1);
      bytes[10] = (byte) month.charAt(2);
      bytes[11] = '-';
      int year = calendar.get(Calendar.YEAR);
      bytes[12] = (byte) ('0' + year / 1000);
      bytes[13] = (byte) ('0' + year / 100 % 10);
      bytes[14] = (byte) ('0' + year / 10 % 10);
      bytes[15] = (byte) ('0' + year % 10);
      bytes[16] = ' ';
      int hour = calendar.get(Calendar.HOUR_OF_DAY);
      bytes[17] = (byte) ('0' + hour / 10);
      bytes[18] = (byte) ('0' + hour % 10);
      bytes[19] = ':';
      int minute = calendar.get(Calendar.MINUTE);
      bytes[20] = (byte) ('0' + minute / 10);
      bytes[21] = (byte) ('0' + minute % 10);
      bytes[22] = ':';
      int second = calendar.get(Calendar.SECOND);
      bytes[23] = (byte) ('0' + second / 10);
      bytes[24] = (byte) ('0' + second % 10);
      bytes[25] = ' ';
      bytes[26] = 'G';
      bytes[27] = 'M';
      bytes[28] = 'T';
      return new AsciiString(bytes, false);
   }

   public static boolean authorityMatch(CharSequence path, CharSequence authority, String prefix, String defaultPort) {
      int colonIndex = indexOf(authority, 0, ':');
      if (!AsciiString.regionMatches(path, false, prefix.length(), authority, 0, colonIndex)) {
         return false;
      }
      if (path.charAt(prefix.length() + colonIndex) == ':') {
         CharSequence port;
         int portOffset, portLength;
         if (authority.length() == colonIndex) {
            port = defaultPort;
            portOffset = 0;
            portLength = defaultPort.length();
         } else {
            port = authority;
            portOffset = colonIndex + 1;
            portLength = authority.length() - colonIndex - 1;
         }
         return AsciiString.regionMatches(path, false, prefix.length() + colonIndex, port, portOffset, portLength);
      } else {
         return colonIndex == authority.length() ||
               colonIndex == authority.length() - defaultPort.length() - 1 &&
                     AsciiString.regionMatches(authority, false, authority.length() - defaultPort.length(), defaultPort, 0, defaultPort.length());
      }
   }
}

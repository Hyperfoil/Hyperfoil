package io.hyperfoil.http;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.impl.Util;
import io.netty.buffer.ByteBuf;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.FastThreadLocal;

public final class HttpUtil {
   private static final Logger log = LogManager.getLogger(HttpUtil.class);

   private static final AsciiString UTC_ASCII = new AsciiString("UTC");
   private static final AsciiString GMT_ASCII = new AsciiString("GMT");
   private static final TimeZone UTC = TimeZone.getTimeZone(UTC_ASCII.toString());
   private static final TimeZone GMT = TimeZone.getTimeZone(GMT_ASCII.toString());
   private static final FastThreadLocal<Map<AsciiString, Calendar>> CALENDARS = new FastThreadLocal<>() {
      @Override
      protected Map<AsciiString, Calendar> initialValue() {
         return Map.of(
               UTC_ASCII, Calendar.getInstance(UTC),
               GMT_ASCII, Calendar.getInstance(GMT));
      }
   };

   private static final CharSequence[] MONTHS = { "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov",
         "dec" };

   private static final byte[] BYTES_80 = "80".getBytes(StandardCharsets.UTF_8);
   private static final byte[] BYTES_443 = "443".getBytes(StandardCharsets.UTF_8);

   public static final String HTTP_PREFIX = "http://";
   public static final String HTTPS_PREFIX = "https://";

   private HttpUtil() {
   }

   public static int indexOf(CharSequence seq, int begin, char c) {
      int length = seq.length();
      for (int i = begin; i < length; ++i) {
         if (seq.charAt(i) == c) {
            return i;
         }
      }
      return length;
   }

   public static int lastIndexOf(CharSequence seq, int end, char c) {
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

   public static long parseDate(CharSequence seq, int begin, int end) {
      int i = begin;
      for (; i < end && seq.charAt(i) != ','; ++i)
         ; // skip day-of-week
      ++i; // skip the comma
      for (; i < end && seq.charAt(i) == ' '; ++i)
         ; // skip spaces
      if (i + 2 >= end) {
         log.warn("Cannot parse date {}", seq.subSequence(begin, end));
         return 0;
      }
      int dayOfMonth;
      if (seq.charAt(i + 1) == ' ') {
         dayOfMonth = seq.charAt(i) - '0';
         i += 2; // single digit and ' '
      } else {
         dayOfMonth = twoDigits(seq, i);
         i += 3; // two digits and ' '
      }
      if (dayOfMonth < 1 || dayOfMonth > 31) {
         log.warn("Cannot parse date {}", seq.subSequence(begin, end));
         return 0;
      }
      if (i + 3 >= end) {
         log.warn("Cannot parse date {}", seq.subSequence(begin, end));
         return 0;
      }
      int month = -1;
      for (int m = 0; m < MONTHS.length; ++m) {
         if (Character.toLowerCase(seq.charAt(i)) == MONTHS[m].charAt(0) &&
               Character.toLowerCase(seq.charAt(i + 1)) == MONTHS[m].charAt(1) &&
               Character.toLowerCase(seq.charAt(i + 2)) == MONTHS[m].charAt(2)) {
            month = m;
            break;
         }
      }
      if (month < 0) {
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
      for (i = nextSpace + 1; i < end && seq.charAt(i) == ' '; ++i)
         ; // skip spaces

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
      for (i += 8; i < end && seq.charAt(i) == ' '; ++i)
         ; // skip spaces

      Calendar calendar;
      if (i < end) {
         if (end - i >= 3 && AsciiString.regionMatches(seq, false, i, GMT_ASCII, 0, 3)) {
            calendar = CALENDARS.get().get(GMT_ASCII);
         } else if (end - i >= 3 && AsciiString.regionMatches(seq, false, i, UTC_ASCII, 0, 3)) {
            calendar = CALENDARS.get().get(UTC_ASCII);
         } else {
            // allocate a new calendar only if not UTC or GMT
            TimeZone timeZone = TimeZone.getTimeZone(seq.subSequence(i, end).toString());
            calendar = Calendar.getInstance(timeZone);
         }
      } else {
         calendar = CALENDARS.get().get(UTC_ASCII);
      }

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
            bytes[0] = 'S';
            bytes[1] = 'u';
            bytes[2] = 'n';
            break;
         case Calendar.MONDAY:
            bytes[0] = 'M';
            bytes[1] = 'o';
            bytes[2] = 'n';
            break;
         case Calendar.TUESDAY:
            bytes[0] = 'T';
            bytes[1] = 'u';
            bytes[2] = 'e';
            break;
         case Calendar.WEDNESDAY:
            bytes[0] = 'W';
            bytes[1] = 'e';
            bytes[2] = 'd';
            break;
         case Calendar.THURSDAY:
            bytes[0] = 'T';
            bytes[1] = 'h';
            bytes[2] = 'u';
            break;
         case Calendar.FRIDAY:
            bytes[0] = 'F';
            bytes[1] = 'r';
            bytes[2] = 'i';
            break;
         case Calendar.SATURDAY:
            bytes[0] = 'S';
            bytes[1] = 'a';
            bytes[2] = 't';
            break;
      }
      bytes[3] = ',';
      bytes[4] = ' ';
      int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      bytes[5] = (byte) ('0' + dayOfMonth / 10);
      bytes[6] = (byte) ('0' + dayOfMonth % 10);
      bytes[7] = '-';
      CharSequence month = MONTHS[calendar.get(Calendar.MONTH)];
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

   public static boolean authorityMatch(CharSequence path, CharSequence authority, boolean isHttp) {
      return isHttp ? authorityMatchHttp(path, authority) : authorityMatchHttps(path, authority);
   }

   public static boolean authorityMatchHttp(CharSequence path, CharSequence authority) {
      return authorityMatch(path, authority, "80", HTTP_PREFIX.length());
   }

   public static boolean authorityMatchHttps(CharSequence path, CharSequence authority) {
      return authorityMatch(path, authority, "443", HTTPS_PREFIX.length());
   }

   public static boolean authorityMatch(CharSequence path, CharSequence authority, String defaultPort, int prefixLength) {
      int colonIndex = indexOf(authority, 0, ':');
      // hostname match is case-insensitive
      if (!AsciiString.regionMatches(path, true, prefixLength, authority, 0, colonIndex)) {
         return false;
      }
      if (prefixLength + colonIndex < path.length() && path.charAt(prefixLength + colonIndex) == ':') {
         // path uses explicit port
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
         return AsciiString.regionMatches(path, false, prefixLength + colonIndex + 1, port, portOffset, portLength);
      } else {
         return colonIndex == authority.length() ||
               colonIndex == authority.length() - defaultPort.length() - 1 &&
                     AsciiString.regionMatches(authority, false, authority.length() - defaultPort.length(), defaultPort, 0,
                           defaultPort.length());
      }
   }

   public static boolean authorityMatch(ByteBuf pathData, int pathOffset, int pathLength, byte[] authority, boolean isHttp) {
      return isHttp ? authorityMatchHttp(pathData, pathOffset, pathLength, authority)
            : authorityMatchHttps(pathData, pathOffset, pathLength, authority);
   }

   public static boolean authorityMatchHttp(ByteBuf pathData, int pathOffset, int pathLength, byte[] authority) {
      return authorityMatch(pathData, pathOffset, pathLength, authority, BYTES_80, HTTP_PREFIX.length());
   }

   public static boolean authorityMatchHttps(ByteBuf pathData, int pathOffset, int pathLength, byte[] authority) {
      return authorityMatch(pathData, pathOffset, pathLength, authority, BYTES_443, HTTPS_PREFIX.length());
   }

   public static boolean authorityMatch(ByteBuf pathData, int pathOffset, int pathLength, byte[] authority, byte[] defaultPort,
         int prefixLength) {
      int colonIndex = indexOf(authority, (byte) ':');
      // For simplicity we won't bother with case-insensitive match
      if (!regionMatches(pathData, pathOffset + prefixLength, pathLength - prefixLength, authority, 0, colonIndex)) {
         return false;
      }
      if (pathData.getByte(prefixLength + colonIndex) == ':') {
         // path uses explicit port
         byte[] port;
         int portOffset, portLength;
         if (authority.length == colonIndex) {
            port = defaultPort;
            portOffset = 0;
            portLength = defaultPort.length;
         } else {
            port = authority;
            portOffset = colonIndex + 1;
            portLength = authority.length - colonIndex - 1;
         }
         return regionMatches(pathData, pathOffset + prefixLength + colonIndex, pathLength - prefixLength - colonIndex, port,
               portOffset, portLength);
      } else {
         return colonIndex == authority.length ||
               colonIndex == authority.length - defaultPort.length - 1 &&
                     Arrays.equals(authority, authority.length - defaultPort.length, authority.length, defaultPort, 0,
                           defaultPort.length);
      }
   }

   private static boolean regionMatches(ByteBuf data, int offset, int dataLength, byte[] bytes, int bs, int length) {
      if (dataLength < length) {
         return false;
      }
      assert bytes.length >= bs + length;
      for (int i = 0; i < length; ++i) {
         if (data.getByte(offset + i) != bytes[bs + i]) {
            return false;
         }
      }
      return true;
   }

   static int indexOf(byte[] bytes, byte b) {
      for (int i = 0; i <= bytes.length; ++i) {
         if (bytes[i] == b) {
            return i;
         }
      }
      return bytes.length;
   }

   public static int indexOf(ByteBuf data, int offset, int length, char c) {
      for (int i = 0; i <= length; ++i) {
         if (data.getByte(offset + i) == c) {
            return i;
         }
      }
      return length;
   }

   public static int prefixLength(boolean isHttp) {
      return isHttp ? HTTP_PREFIX.length() : HTTPS_PREFIX.length();
   }
}

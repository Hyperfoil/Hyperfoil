package io.sailrocket.core.util;

import java.util.concurrent.TimeUnit;

import io.sailrocket.api.config.BenchmarkDefinitionException;

public class Util {
   private Util() {}

   public static long parseToNanos(String time) {
      TimeUnit unit;
      String prefix;
      if (time.endsWith("ms")) {
         unit = TimeUnit.MILLISECONDS;
         prefix = time.substring(0, time.length() - 2);
      } else if (time.endsWith("us")) {
         unit = TimeUnit.MICROSECONDS;
         prefix = time.substring(0, time.length() - 2);
      } else if (time.endsWith("ns")) {
         unit = TimeUnit.NANOSECONDS;
         prefix = time.substring(0, time.length() - 2);
      } else if (time.endsWith("s")) {
         unit = TimeUnit.SECONDS;
         prefix = time.substring(0, time.length() - 1);
      } else if (time.endsWith("m")) {
         unit = TimeUnit.MINUTES;
         prefix = time.substring(0, time.length() - 1);
      } else if (time.endsWith("h")) {
         unit = TimeUnit.HOURS;
         prefix = time.substring(0, time.length() - 1);
      } else {
         throw new BenchmarkDefinitionException("Unknown time unit: " + time);
      }
      return unit.toNanos(Long.parseLong(prefix));
   }

   public static long parseToMillis(String time) {
      TimeUnit unit;
      String prefix;
      switch (time.charAt(time.length() - 1)) {
         case 's':
            unit = TimeUnit.SECONDS;
            prefix = time.substring(0, time.length() - 1);
            break;
         case 'm':
            unit = TimeUnit.MINUTES;
            prefix = time.substring(0, time.length() - 1);
            break;
         case 'h':
            unit = TimeUnit.HOURS;
            prefix = time.substring(0, time.length() - 1);
            break;
         default:
            unit = TimeUnit.SECONDS;
            prefix = time;
            break;
      }
      return unit.toMillis(Long.parseLong(prefix));
   }
}

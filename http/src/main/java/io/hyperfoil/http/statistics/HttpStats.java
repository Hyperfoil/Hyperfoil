package io.hyperfoil.http.statistics;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.annotation.JsonTypeName;

import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.api.statistics.StatsExtension;

@MetaInfServices(StatsExtension.class)
@JsonTypeName("http")
public class HttpStats implements StatsExtension {
   public static final String HTTP = "http";

   private static final Statistics.LongUpdater<HttpStats> ADD_STATUS = (s, value) -> {
      switch ((int) value / 100) {
         case 2:
            s.status_2xx++;
            break;
         case 3:
            s.status_3xx++;
            break;
         case 4:
            s.status_4xx++;
            break;
         case 5:
            s.status_5xx++;
            break;
         default:
            s.status_other++;
      }
   };
   private static final Statistics.LongUpdater<HttpStats> ADD_CACHE_HIT = (s, ignored) -> s.cacheHits++;
   private static final String[] HEADERS = { "2xx", "3xx", "4xx", "5xx", "OtherStatus", "CacheHits" };

   public int status_2xx;
   public int status_3xx;
   public int status_4xx;
   public int status_5xx;
   public int status_other;
   public int cacheHits;

   public static void addStatus(Statistics statistics, long timestamp, int status) {
      statistics.update(HTTP, timestamp, HttpStats::new, HttpStats.ADD_STATUS, status);
   }

   public static void addCacheHit(Statistics statistics, long timestamp) {
      statistics.update(HTTP, timestamp, HttpStats::new, HttpStats.ADD_CACHE_HIT, 1);
   }

   public static HttpStats get(StatisticsSnapshot snapshot) {
      StatsExtension stats = snapshot.extensions.get(HTTP);
      if (stats == null) {
         // return empty to prevent NPEs
         return new HttpStats();
      }
      return (HttpStats) stats;
   }

   public static HttpStats get(StatisticsSummary summary) {
      StatsExtension stats = summary.extensions.get(HTTP);
      if (stats == null) {
         // return empty to prevent NPEs
         return new HttpStats();
      }
      return (HttpStats) stats;
   }

   public int[] statuses() {
      return new int[] { status_2xx, status_3xx, status_4xx, status_5xx, status_other };
   }

   @Override
   public void reset() {
      status_2xx = 0;
      status_3xx = 0;
      status_4xx = 0;
      status_5xx = 0;
      status_other = 0;
      cacheHits = 0;
   }

   @Override
   public HttpStats clone() {
      HttpStats copy = new HttpStats();
      copy.add(this);
      return copy;
   }

   @Override
   public String[] headers() {
      return HEADERS;
   }

   @Override
   public String byHeader(String header) {
      switch (header) {
         case "2xx":
            return String.valueOf(status_2xx);
         case "3xx":
            return String.valueOf(status_3xx);
         case "4xx":
            return String.valueOf(status_4xx);
         case "5xx":
            return String.valueOf(status_5xx);
         case "OtherStatus":
            return String.valueOf(status_other);
         case "CacheHits":
            return String.valueOf(cacheHits);
         default:
            return "<unknown header: " + header + ">";
      }
   }

   @Override
   public boolean isNull() {
      return status_2xx + status_3xx + status_4xx + status_5xx + status_other + cacheHits == 0;
   }

   @Override
   public void add(StatsExtension other) {
      if (other instanceof HttpStats) {
         HttpStats o = (HttpStats) other;
         status_2xx += o.status_2xx;
         status_3xx += o.status_3xx;
         status_4xx += o.status_4xx;
         status_5xx += o.status_5xx;
         status_other += o.status_other;
         cacheHits += o.cacheHits;
      } else {
         throw new IllegalArgumentException(other.toString());
      }
   }

   @Override
   public void subtract(StatsExtension other) {
      if (other instanceof HttpStats) {
         HttpStats o = (HttpStats) other;
         status_2xx -= o.status_2xx;
         status_3xx -= o.status_3xx;
         status_4xx -= o.status_4xx;
         status_5xx -= o.status_5xx;
         status_other -= o.status_other;
         cacheHits -= o.cacheHits;
      } else {
         throw new IllegalArgumentException(other.toString());
      }
   }

   @Override
   public String toString() {
      return '{' +
            ", status_2xx=" + status_2xx +
            ", status_3xx=" + status_3xx +
            ", status_4xx=" + status_4xx +
            ", status_5xx=" + status_5xx +
            ", status_other=" + status_other +
            ", cacheHits=" + cacheHits
            + '}';
   }
}

package io.sailrocket.api;

import org.HdrHistogram.Histogram;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

//TODO:: tidy this up, just simple for POC
public class SequenceStatistics {
    public final Histogram histogram = new Histogram(TimeUnit.MINUTES.toNanos(1), 2);
    public int connectFailureCount;
    public int requestCount;
    public int responseCount;
    public int status_2xx;
    public int status_3xx;
    public int status_4xx;
    public int status_5xx;
    public int status_other;
    public int resetCount;

    public int[] statuses() {
       return new int[] { status_2xx, status_3xx, status_4xx, status_5xx, status_other };
    }

    public void addStatus(int code) {
       switch (code / 100) {
          case 2:
             status_2xx++;
             break;
          case 3:
             status_3xx++;
             break;
          case 4:
             status_4xx++;
             break;
          case 5:
             status_5xx++;
             break;
          default:
             status_other++;
       }
    }

   @Override
   public String toString() {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (PrintStream ps = new PrintStream(baos, true, "UTF-8")) {
         ps.println("ConnectFailureCount: " + connectFailureCount);
         ps.println("RequestCount: " + requestCount);
         ps.println("ResponseCount: " + responseCount);
         ps.println("Status_2xx: " + status_2xx);
         ps.println("Status_3xx: " + status_3xx);
         ps.println("Status_4xx: " + status_4xx);
         ps.println("Status_5xx: " + status_5xx);
         ps.println("Status_other: " + status_other);
         ps.println("ResetCount: " + resetCount);
         histogram.outputPercentileDistribution(ps, 1_000_000.0);
      } catch (UnsupportedEncodingException e) {
         throw new IllegalStateException(e);
      }
      return new String(baos.toByteArray(), StandardCharsets.UTF_8);
   }
}

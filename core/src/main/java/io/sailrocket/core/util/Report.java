package io.sailrocket.core.util;

import io.vertx.core.json.JsonObject;
import org.HdrHistogram.Histogram;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Report {
    static final String[] COLUMNS = {
            "req_s", "responseCount", "responseErrors", "expectedRequests", "ratio",
            "bytesRead", "bytesWritten",
            "min", "p50", "p90", "p99", "p999", "p9999", "max"
    };
    long expectedRequests;
    long elapsed;
    public Histogram histogram;
    int responseCount;
    public double ratio;
    int connectFailureCount;
    int resetCount;
    int requestCount;
    int[] statuses;
    long bytesRead;
    long byteWritten;
    final String[] tagFields;
    final JsonObject results;

    public Report(JsonObject tags) {
        this.results = tags;
        this.tagFields = tags.fieldNames().toArray(new String[tags.fieldNames().size()]);
        Arrays.sort(tagFields);
    }

    public void measures(long expectedRequests, long elapsed, Histogram histogram, int responseCount, double ratio,
                         int connectFailureCount, int resetCount, int requestCount, int[] statuses,
                         long bytesRead, long bytesWritten) {

        double elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsed);
        this.results.put("req_s", Math.round((responseCount / elapsedSeconds) * 100) / 100);
        this.results.put("expectedRequests", expectedRequests);
        this.results.put("elapsed", elapsed);
        this.results.put("responseCount", responseCount);
        this.results.put("ratio", Math.round(ratio * 100) / 100);
        this.results.put("connectFailureCount", connectFailureCount);
        this.results.put("responseErrors", connectFailureCount + resetCount);
        this.results.put("requestCount", requestCount);
        this.results.put("bytesRead", bytesRead);
        this.results.put("bytesWritten", bytesWritten);

        this.expectedRequests = expectedRequests;
        this.elapsed = elapsed;
        this.histogram = histogram;
        this.responseCount = responseCount;
        this.ratio = ratio;
        this.connectFailureCount = connectFailureCount;
        this.resetCount = resetCount;
        this.requestCount = requestCount;
        this.statuses = statuses;
        this.bytesRead = bytesRead;
        this.byteWritten = bytesWritten;
    }

    public void prettyPrint() {
        double elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsed);
        System.out.format("finished in %.2fs, %.2fs req/s, %.2fs ratio%n", elapsedSeconds, responseCount / elapsedSeconds, ratio);
        System.out.format("responses: %d total, %d errored, %d expected%n", responseCount, connectFailureCount + resetCount, expectedRequests);
        System.out.format("status codes: %d 2xx, %d 3xx, %d 4xx, %d 5xx, %d others%n", statuses[0], statuses[1], statuses[2], statuses[3], statuses[4]);
        System.out.format("bytes read: %d%n", bytesRead);
        System.out.format("bytes written: %d%n", byteWritten);
        System.out.format("missed requests: %d%n", expectedRequests - requestCount);
        System.out.println("min    = " + getMinResponseTimeMillis());
        System.out.println("max    = " + getMaxResponseTimeMillis());
        System.out.println("50%    = " + getResponseTimeMillisPercentile(50));
        System.out.println("90%    = " + getResponseTimeMillisPercentile(90));
        System.out.println("99%    = " + getResponseTimeMillisPercentile(99));
        System.out.println("99.9%  = " + getResponseTimeMillisPercentile(99.9));
        System.out.println("99.99% = " + getResponseTimeMillisPercentile(99.99));
    }

    String[] columns() {
        List<String> columns = new ArrayList<>();
        columns.addAll(Arrays.asList(COLUMNS));
        columns.addAll(Arrays.asList(tagFields));
        return columns.toArray(new String[columns.size()]);
    }

    public String header() {
        return Arrays.stream(columns()).collect(Collectors.joining(",")) + "\n";
    }

    public String format(String[] columns) {
        if (columns == null) {
            columns = columns();
        }
        List<String> row = new ArrayList<>();
        double elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsed);
        double req_s = responseCount / elapsedSeconds;
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case "min":
                    row.add(String.valueOf(getMinResponseTimeMillis()));
                    break;
                case "max":
                    row.add(String.valueOf(getMaxResponseTimeMillis()));
                    break;
                case "p50":
                    row.add(String.valueOf(getResponseTimeMillisPercentile(50)));
                    break;
                case "p90":
                    row.add(String.valueOf(getResponseTimeMillisPercentile(90)));
                    break;
                case "p99":
                    row.add(String.valueOf(getResponseTimeMillisPercentile(99)));
                    break;
                case "p999":
                    row.add(String.valueOf(getResponseTimeMillisPercentile(99.9)));
                    break;
                case "p9999":
                    row.add(String.valueOf(getResponseTimeMillisPercentile(99.99)));
                    break;
                default:
                    row.add(String.valueOf(results.getValue(columns[i])));
            }
        }
        return row.stream().collect(Collectors.joining(",")) + "\n";
    }

    long getMinResponseTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(histogram.getMinValue());
    }

    public long getMaxResponseTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(histogram.getMaxValue());
    }

    public long getResponseTimeMillisPercentile(double x) {
        return TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(x));
    }

    public void save(String baseName) {
        try (PrintStream ps = new PrintStream(baseName + ".hdr")) {
            histogram.outputPercentileDistribution(ps, 1000000.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

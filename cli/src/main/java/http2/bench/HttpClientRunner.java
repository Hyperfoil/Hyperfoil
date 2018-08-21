package http2.bench;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.Report;
import io.sailrocket.api.SequenceStatistics;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.impl.ScenarioImpl;
import io.sailrocket.core.impl.SequenceImpl;
import io.sailrocket.core.impl.SimulationImpl;
import io.sailrocket.core.impl.StepImpl;
import io.sailrocket.core.impl.statistics.PrintStatisticsConsumer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import org.HdrHistogram.Histogram;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Todo : use pool buffers wherever possible
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpClientRunner {

    public long currentTime;

    public HttpClientProvider provider = HttpClientProvider.netty;

    public HttpVersion protocol = HttpVersion.HTTP_2;

    public String durationParam = "30s";

    public int connections = 1;

    public String out = null;

    public String bodyParam = null;

    public int concurrency = 1;

    public List<String> uriParam;

    public List<Integer> rates = Collections.singletonList(100); // rate per second

    public String warmupParam = "0";

    public int threads = 1;

    public String tagString = null;

    private ByteBuf payload;
    private JsonObject tags = new JsonObject();

    public HttpClientRunner() {
    }

    public HttpClientRunner(HttpClientProvider provider,
                            HttpVersion protocol,
                            String durationParam,
                            int connections,
                            String out,
                            String bodyParam,
                            int concurrency,
                            List<String> uriParam,
                            List<Integer> rates,
                            String warmupParam,
                            int threads,
                            String tagString) {
        this.provider = provider;
        this.protocol = protocol;
        this.durationParam = durationParam;
        this.connections = connections;
        this.out = out;
        this.bodyParam = bodyParam;
        this.concurrency = concurrency;
        this.uriParam = uriParam;
        this.rates = rates;
        this.warmupParam = warmupParam;
        this.threads = threads;
        this.tagString = tagString;
    }

    private static long parseDuration(String s) {
        TimeUnit unit;
        String prefix;
        switch (s.charAt(s.length() - 1)) {
            case 's':
                unit = TimeUnit.SECONDS;
                prefix = s.substring(0, s.length() - 1);
                break;
            case 'm':
                unit = TimeUnit.MINUTES;
                prefix = s.substring(0, s.length() - 1);
                break;
            case 'h':
                unit = TimeUnit.HOURS;
                prefix = s.substring(0, s.length() - 1);
                break;
            default:
                unit = TimeUnit.SECONDS;
                prefix = s;
                break;
        }
        return unit.toNanos(Long.parseLong(prefix));
    }

    public Histogram run() throws Exception {
        if (tagString != null) {
            for (String tag : tagString.split(",")) {
                String[] components = tag.trim().split("=");
                tags.put(components[0], components[1]);
            }
        }

        long duration = parseDuration(durationParam);
        long warmup = parseDuration(warmupParam);
        tags.put("payloadSize", 0);

        if (bodyParam != null) {
            try {
                int size = Integer.parseInt(bodyParam);
                if (size > 0) {
                    byte[] bytes = new byte[size];
                    Random r = new Random();
                    for (int i = 0; i < bytes.length; i++) {
                        bytes[i] = (byte) ('A' + r.nextInt(27));
                    }
                    payload = Buffer.buffer(bytes).getByteBuf();
                    tags.put("payloadSize", size);
                }
            } catch (NumberFormatException ignore) {
            }
            if (payload == null) {
                File f = new File(bodyParam);
                if (!f.exists() || !f.isFile()) {
                    throw new Exception("could not open file " + bodyParam);
                }
                payload = Buffer.buffer(Files.readAllBytes(f.toPath())).getByteBuf();
            }
        }

        if (uriParam == null || uriParam.size() < 1) {
            throw new Exception("no URI or input file given");
        }
        tags.put("url", uriParam.get(0));

        URI absoluteURI = new URI(uriParam.get(0));
        String host = absoluteURI.getHost();
        int port = absoluteURI.getPort();
        String path = absoluteURI.getPath();
        boolean ssl = absoluteURI.getScheme().equals("https");

        //TODO:: include in builder
        Consumer<SequenceStatistics> printStatsConsumer = new PrintStatisticsConsumer();

        AtomicReference<SimulationImpl> currentLoad = new AtomicReference<>();
        Timer timer = new Timer("console-logger", true);
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                SimulationImpl simulationImpl = currentLoad.get();
                if (simulationImpl != null) {
                    simulationImpl.printDetails(printStatsConsumer);
                }
            }
        }, TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(5));

        HttpClientPoolFactory httpClientPoolFactory = provider.builder()
                .threads(threads)
                .ssl(ssl)
                .port(port)
                .host(host)
                .size(connections)
                .concurrency(concurrency)
                .protocol(protocol);

        tags.put("rate", 0);
        tags.put("protocol", protocol.toString());
        tags.put("maxQueue", concurrency);
        tags.put("connections", connections);

//    System.out.println("starting benchmark...");
//    System.out.format("%d total connections(s)%n", connections);
        StringBuilder ratesChart = new StringBuilder();
        StringBuilder histoChart = new StringBuilder();
        StringBuilder allReport = new StringBuilder();
        Report report = new Report(tags);
        allReport.append(report.header());
        double[] percentiles = {50, 90, 99, 99.9};
        for (int rate : rates) {
            tags.put("rate", rate);
            tags.put("threads", threads);
            //build simple simulation with one step
            SimulationImpl simulationImpl = buildSimulation(threads, rate, duration, warmup, httpClientPoolFactory, path, payload, tags);
//      new SimulationImpl(threads, rate, duration, warmup, httpClientPoolFactory, path, payload, tags);
            currentLoad.set(simulationImpl);
            report = simulationImpl.run().stream().findFirst().get();
            currentLoad.set(null);
//      report.prettyPrint();
            if (out != null) {
                report.save(out + "_" + rate);
            }
            ratesChart.append(rate).append(",").append(report.ratio).append("\n");
            histoChart.append(rate);
            for (double percentile : percentiles) {
                histoChart.append(",").append(report.getResponseTimeMillisPercentile(percentile));
            }
            histoChart.append(",").append(report.getMaxResponseTimeMillis());
            histoChart.append("\n");
            allReport.append(report.format(null));
        }

        if (out != null) {
            try (PrintStream ps = new PrintStream(out + "_rates.csv")) {
                ps.print(ratesChart);
            }
            try (PrintStream ps = new PrintStream(out + "_histo.csv")) {
                ps.print(histoChart);
            }
            try (PrintStream ps = new PrintStream(out + "_report.csv")) {
                ps.print(allReport);
            }
        }
        httpClientPoolFactory.shutdown();
        timer.cancel();
        return report.histogram;
    }

    private SimulationImpl buildSimulation(int threads, int rate, long duration, long warmup,
                                           HttpClientPoolFactory clientBuilder, String path,
                                           ByteBuf payload, JsonObject tags) { //TODO:: incorporate payload

        SimulationImpl simulation = new SimulationImpl(threads, rate, duration, warmup, clientBuilder, tags);

        simulation.scenario(
                new ScenarioImpl().sequence(
                        new SequenceImpl().step(
                                new StepImpl().path(path))
                )
        );

        return simulation;

    }
}

package io.sailrocket.core;

import io.sailrocket.api.Benchmark;
import io.sailrocket.core.client.SimulationImpl;
import io.sailrocket.core.util.Report;
import org.HdrHistogram.Histogram;

import java.io.PrintStream;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.sailrocket.core.builders.ScenarioBuilder.scenarioBuilder;
import static io.sailrocket.core.builders.SequenceBuilder.sequenceBuilder;
import static io.sailrocket.core.builders.StepBuilder.stepBuilder;

public class BenchmarkImpl extends Benchmark {
    public BenchmarkImpl(String name) {
        super(name);
    }

    @Override
    public Histogram run() {

        //if we dont have any simulations, use a simple one from the endpoint
        if(endpoint != null && ((SimulationImpl) simulation).numOfScenarios() == 0) {
            simulation.scenario(scenarioBuilder()
                                        .sequence(sequenceBuilder()
                                        .step(stepBuilder()
                                                .path(endpoint)
                                              )
                                        )
            .build());
        }

        double[] percentiles = {50, 90, 99, 99.9};
        AtomicReference<SimulationImpl> currentLoad = new AtomicReference<>();
        Timer timer = new Timer("console-logger", true);
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                SimulationImpl simulationImpl = currentLoad.get();
                if (simulationImpl != null) {
                    simulationImpl.printDetails();
                }
            }
        }, TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(5));

        StringBuilder ratesChart = new StringBuilder();
        StringBuilder histoChart = new StringBuilder();
        StringBuilder allReport = new StringBuilder();
        Report report = new Report(((SimulationImpl) simulation).tags());
        allReport.append(report.header());

        currentLoad.set((SimulationImpl) simulation);

        try {
            report = ((SimulationImpl) simulation).run().get(0);
            currentLoad.set(null);

            if (name != null) {
                report.save(name + "_" + ((SimulationImpl) simulation).rate());
            }
            ratesChart.append(((SimulationImpl) simulation).rate()).append(",").append(report.ratio).append("\n");
            histoChart.append(((SimulationImpl) simulation).rate());
            for (double percentile : percentiles) {
                histoChart.append(",").append(report.getResponseTimeMillisPercentile(percentile));
            }
            histoChart.append(",").append(report.getMaxResponseTimeMillis());
            histoChart.append("\n");
            allReport.append(report.format(null));

        if (name != null) {
            try (PrintStream ps = new PrintStream(name + "_rates.csv")) {
                ps.print(ratesChart);
            }
            try (PrintStream ps = new PrintStream(name + "_histo.csv")) {
                ps.print(histoChart);
            }
            try (PrintStream ps = new PrintStream(name + "_report.csv")) {
                ps.print(allReport);
            }
        }

        ((SimulationImpl) simulation).shutdown();
        timer.cancel();
        return report.histogram;
        }
        catch(Exception e) {
            e.printStackTrace();
            return null;
        }

    /*
        HttpClientRunner httpClient = new HttpClientRunner();
        httpClient.provider = HttpClientProvider.vertx;
        httpClient.connections = this.users;
        httpClient.threads = 4;
        httpClient.uriParam = Arrays.asList(this.endpoint);
        httpClient.durationParam = "5s";
        httpClient.concurrency = 1;
        httpClient.rates = Arrays.asList(1000);
        httpClient.protocol = HttpVersion.HTTP_1_1;

        try {
            return httpClient.run();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        */
    }


}

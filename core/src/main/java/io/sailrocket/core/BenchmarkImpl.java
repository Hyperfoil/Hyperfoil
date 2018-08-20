package io.sailrocket.core;

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.Report;
import io.sailrocket.core.client.SimulationImpl;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Optional;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BenchmarkImpl extends Benchmark {
    public BenchmarkImpl(String name) {
        super(name);
    }

    @Override
    public Collection<Report> run() throws BenchmarkDefinitionException {

        //if we dont have any simulations, use a simple one from the endpoint
        //TODO:: this needs to be moved into the cli to build a "default" scenario
        if(((SimulationImpl) simulation).numOfScenarios() == 0) {
            throw new BenchmarkDefinitionException("No Scenarios have been defined");
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
//        StringBuilder allReport = new StringBuilder();

        currentLoad.set((SimulationImpl) simulation);


//        allReport.append(report.header());


        Collection<Report> reports = null;
        try {

            reports = ((SimulationImpl) simulation).run();

            Optional<Report> possibleReport = reports.stream().findFirst();
            if(possibleReport.isPresent()) {
                Report report = possibleReport.get();
                currentLoad.set(null);

                if(name != null) {
                    report.save(name + "_" + ((SimulationImpl) simulation).rate());
                }
                ratesChart.append(((SimulationImpl) simulation).rate()).append(",").append(report.ratio).append("\n");
                histoChart.append(((SimulationImpl) simulation).rate());
                for(double percentile : percentiles) {
                    histoChart.append(",").append(report.getResponseTimeMillisPercentile(percentile));
                }
                histoChart.append(",").append(report.getMaxResponseTimeMillis());
                histoChart.append("\n");
//            allReport.append(report.format(null));

                if(name != null) {
                    try(PrintStream ps = new PrintStream(name + "_rates.csv")) {
                        ps.print(ratesChart);
                    }
                    try(PrintStream ps = new PrintStream(name + "_histo.csv")) {
                        ps.print(histoChart);
                    }
                    try(PrintStream ps = new PrintStream(name + "_report.csv")) {
//                ps.print(allReport);
                    }
                }
            }

            ((SimulationImpl) simulation).shutdown();
            timer.cancel();
            return reports;
        }
        catch(Exception e) {
            e.printStackTrace();
            return null;
        }

    }


}

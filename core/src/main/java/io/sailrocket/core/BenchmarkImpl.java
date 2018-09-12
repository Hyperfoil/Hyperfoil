package io.sailrocket.core;

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.Report;
import io.sailrocket.core.impl.statistics.PrintStatisticsConsumer;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

public class BenchmarkImpl extends Benchmark {
    public BenchmarkImpl(String name) {
        super(name);
    }

    @Override
    public Map<String, Report> run() throws BenchmarkDefinitionException {

        if (simulation.phases().isEmpty()) {
            throw new BenchmarkDefinitionException("No phases/scenarios have been defined");
        }

        //TODO:: define in builder
        PrintStatisticsConsumer printStatsConsumer = new PrintStatisticsConsumer(simulation);

        Timer timer = new Timer("console-logger", true);
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                System.out.println("Statistics: ");
                simulation.visitSessions(printStatsConsumer);
                printStatsConsumer.print();
            }
        }, TimeUnit.SECONDS.toMillis(3), TimeUnit.SECONDS.toMillis(3));

        Map<String, Report> reports;
        try {
            reports = simulation.run();
            simulation.shutdown();
            timer.cancel();
            return reports;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

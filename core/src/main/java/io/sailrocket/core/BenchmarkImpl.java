package io.sailrocket.core;

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.Report;
import io.sailrocket.api.SequenceStatistics;
import io.sailrocket.core.impl.SimulationImpl;
import io.sailrocket.core.impl.statistics.PrintStatisticsConsumer;

import java.util.Collection;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class BenchmarkImpl extends Benchmark {
    public BenchmarkImpl(String name) {
        super(name);
    }

    @Override
    public Collection<Report> run() throws BenchmarkDefinitionException {

        if (((SimulationImpl) simulation).numOfScenarios() == 0) {
            throw new BenchmarkDefinitionException("No Scenarios have been defined");
        }

        //TODO:: define in builder
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

        currentLoad.set((SimulationImpl) simulation);


        Collection<Report> reports = null;
        try {

            reports = ((SimulationImpl) simulation).run();

            ((SimulationImpl) simulation).shutdown();
            timer.cancel();
            return reports;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }


}

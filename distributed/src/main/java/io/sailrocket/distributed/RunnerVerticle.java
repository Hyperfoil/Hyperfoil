package io.sailrocket.distributed;

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.Report;
import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.distributed.util.BenchmarkCodec;
import io.sailrocket.distributed.util.ConcurrentHistogramCodec;
import io.sailrocket.distributed.util.HistogramCodec;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.util.Collection;

public class RunnerVerticle extends AbstractVerticle {

    private boolean running = false;
    private EventBus eb;

    @Override
    public void start() {
        eb = vertx.eventBus();
        //TODO:: this is a code smell, not sure atm why i need to register the codec's multiple times
        eb.registerDefaultCodec(Histogram.class, new HistogramCodec());
        eb.registerDefaultCodec(ConcurrentHistogram.class, new ConcurrentHistogramCodec());
        eb.registerDefaultCodec(BenchmarkImpl.class, new BenchmarkCodec());


        eb.consumer("control-feed", message -> {
            if (running)
                message.fail(1, "Benchmark is already running");
            Benchmark toRun = (Benchmark) message.body();
            eb.publish("response-feed", startRunner(toRun));
        });
    }

    //need a Histogram codec to serialize and deserialize histogram
    private Collection<Report> startRunner(Benchmark benchmark) {
        try {
            return benchmark.run();
        } catch (BenchmarkDefinitionException e) {
            //TODO:: handle exception correctly
            e.printStackTrace();
            return null;
        }
    }
}

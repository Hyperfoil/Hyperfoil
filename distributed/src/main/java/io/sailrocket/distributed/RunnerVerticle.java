package io.sailrocket.distributed;

import io.sailrocket.api.Benchmark;
import io.sailrocket.distributed.util.ConcurrentHistogramCodec;
import io.sailrocket.distributed.util.HistogramCodec;
import io.sailrocket.distributed.util.SimpleBenchmarkCodec;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

public class RunnerVerticle extends AbstractVerticle {

    private boolean running = false;
    private EventBus eb;

    @Override
    public void start() {
        eb = vertx.eventBus();
        eb.registerDefaultCodec(Histogram.class, new HistogramCodec());
        eb.registerDefaultCodec(SimpleBenchmark.class, new SimpleBenchmarkCodec());
        eb.registerDefaultCodec(ConcurrentHistogram.class, new ConcurrentHistogramCodec());


        eb.consumer("control-feed", message -> {
            if (running)
                message.fail(1, "Benchmark is already running");
            Benchmark toRun = (Benchmark) message.body();
            eb.publish("response-feed", startRunner(toRun));
        });
    }

    //need a Histogram codec to serialize and deserialize histogram
    private Histogram startRunner(Benchmark benchmark) {
        return benchmark.run();
    }
}

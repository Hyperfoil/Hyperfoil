package io.sailrocket.distributed;

import io.sailrocket.api.Benchmark;
import io.sailrocket.core.BenchmarkImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.HdrHistogram.Histogram;

public class RunnerVerticle extends AbstractVerticle {

    private boolean running = false;
    private EventBus eb;

    @Override
    public void start() throws Exception {
        eb = vertx.eventBus();

        eb.consumer("control-feed", message -> {
            if (running)
                message.fail(1, "Benchmark is already running");

            //TODO:: benchmark control protocol
            // for now we are simply kicking off a benchmark
            // and returning the hdrHistogram when finished

            eb.publish("response-feed", startRunner());
        });
    }

    //need a Histogram codec to serialize and deserialize histogram
    private long startRunner() {

        Benchmark benchmark = new SimpleBenchmark("Simple benchmark");

        return benchmark.run().getTotalCount();

    }


    //TODO:: this definition will be passed into the verticle
    //only used for testing atm
    class SimpleBenchmark extends BenchmarkImpl {


        public SimpleBenchmark(String name) {
            super(name);
            agents("localhost").users(10).endpoint("http://localhost:8080/");
        }
    }

}

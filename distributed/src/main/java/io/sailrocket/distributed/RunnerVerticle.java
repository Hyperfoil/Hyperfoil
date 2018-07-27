package io.sailrocket.distributed;

import io.sailrocket.api.Benchmark;
import io.sailrocket.core.BenchmarkImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class RunnerVerticle extends AbstractVerticle {

    private boolean running = false;
    private EventBus eb;

    @Override
    public void start() throws Exception {

        eb = vertx.eventBus();
        Handler<Message<?>> messageHandler = new RunerMessagehandler();

        eb.consumer("control-feed", message -> messageHandler.handle(message));

        System.out.println("Runner Ready!");

    }


    private class RunerMessagehandler<T> implements Handler<Message<T>> {
        @Override
        public void handle(Message<T> event) {

            System.out.printf("Got message from master '%s'\n", event.body().toString());

            if (running)
                event.fail(1, "Benchmark is already running");


            //TODO:: benchmark control protocol
            // for now we are simply kicking off a benchmark
            // and returning the hdrHistogram when finished
            System.out.println("Starting benchmark!");

            //TODO:: start async
            startRunner();

            //TODO:: reply when done
//            event.reply();

//            event.reply("done");
            eb.publish("response-feed", "finished benchmark!");

        }
    }


    private void startRunner() {

        Benchmark benchmark = new SimpleBenchmark("Simple benchmark");


        benchmark.run();

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

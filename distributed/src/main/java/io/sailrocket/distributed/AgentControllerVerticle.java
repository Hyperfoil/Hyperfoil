package io.sailrocket.distributed;

import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.distributed.util.BenchmarkCodec;
import io.sailrocket.distributed.util.ConcurrentHistogramCodec;
import io.sailrocket.distributed.util.HistogramCodec;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AgentControllerVerticle extends AbstractVerticle {

    private EventBus eb;

    private RoutingContext routingContext;

    private CountDownLatch resultLatch;

    private Histogram collatedHistogram;
    private Map<String, BenchmarkImpl> benchmarks;

    @Override
    public void start() {

        //create http api for controlling and monitoring agent
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.get("/start").handler(this::handleStartBenchmark);
        router.get("/agents").handler(this::handleAgentCount);

        vertx.createHttpServer().requestHandler(router::accept).listen(8090);

        eb = vertx.eventBus();
        //TODO:: this is a code smell, not sure atm why i need to register the codec's multiple times
        eb.registerDefaultCodec(Histogram.class, new HistogramCodec());
        eb.registerDefaultCodec(ConcurrentHistogram.class, new ConcurrentHistogramCodec());
        eb.registerDefaultCodec(BenchmarkImpl.class, new BenchmarkCodec());


        //send response to calling clientPool benchmark has finished
        eb.consumer("response-feed", message -> {
            Histogram histogram = (Histogram) message.body();
            collatedHistogram.add(histogram);
            resultLatch.countDown();
            if (resultLatch.getCount() == 0)
                routingContext.response().putHeader("content-type", "application/json").end(formatJsonHistogram(collatedHistogram));
        });
    }

    private String formatJsonHistogram(Histogram histogram) {
        JsonObject jsonObject = new JsonObject();

        jsonObject.put("count", histogram.getTotalCount());
        jsonObject.put("min", TimeUnit.NANOSECONDS.toMillis(histogram.getMinValue()));
        jsonObject.put("max", TimeUnit.NANOSECONDS.toMillis(histogram.getMaxValue()));
        jsonObject.put("50%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(50)));
        jsonObject.put("90%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(90)));
        jsonObject.put("99%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(99)));
        jsonObject.put("99.9%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(99.9)));
        jsonObject.put("99.99%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(99.99)));

        return jsonObject.encode();


    }

    private void handleAgentCount(RoutingContext routingContext) {
        if (vertx.isClustered()) {
            // a bit hacky, but simple test
            routingContext
                    .response()
                    .setChunked(true)
                    .end(Integer.toString(getNodeCount()));
        }
    }

    private int getNodeCount() {
        return ((VertxImpl) vertx).getClusterManager().getNodes().size();
    }

    private void handleStartBenchmark(RoutingContext routingContext) {
        String benchmarkName = routingContext.request().getParam("benchmark");
        if (benchmarkName != null && benchmarks.containsKey(benchmarkName)) {
            BenchmarkImpl benchmark = benchmarks.get(routingContext.request().getParam("benchmark"));

            eb.publish("control-feed", benchmark);

            collatedHistogram = new ConcurrentHistogram(5);

            resultLatch = new CountDownLatch(getNodeCount() - 1);

        } else {
            //benchmark has not been defined yet
            String msg = "Benchmark not found";
            routingContext.response().setStatusCode(500).setChunked(true).write(msg).end();
        }
        this.routingContext = routingContext;

    }

}
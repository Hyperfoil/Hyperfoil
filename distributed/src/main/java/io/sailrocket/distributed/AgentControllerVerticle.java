package io.sailrocket.distributed;

import io.sailrocket.api.Benchmark;
import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.distributed.util.BenchmarkCodec;
import io.sailrocket.distributed.util.ConcurrentHistogramCodec;
import io.sailrocket.distributed.util.HistogramCodec;
import io.sailrocket.distributed.util.SimpleBenchmarkCodec;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.VertxImpl;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

public class AgentControllerVerticle extends AbstractVerticle {

    private EventBus eb;

    private RoutingContext routingContext;

    @Override
    public void start() {

        //create http api for controlling and monitoring agent
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.get("/start").handler(this::handleStartBenchmark);
        router.get("/agents").handler(this::handleAgentCount);

        vertx.createHttpServer().requestHandler(router::accept).listen(8090);

        eb = vertx.eventBus();
        eb.registerDefaultCodec(Histogram.class, new HistogramCodec());
        eb.registerDefaultCodec(ConcurrentHistogram.class, new ConcurrentHistogramCodec());
        eb.registerDefaultCodec(SimpleBenchmark.class, new SimpleBenchmarkCodec());


        //send response to calling client benchmark has finished
        eb.consumer("response-feed", message -> {
            Histogram histogram = (Histogram) message.body();
            routingContext.response().putHeader("content-type", "application/json").end(
                    Long.toString(
                            histogram.getTotalCount()
                    )
            );
        });
    }

    private void handleAgentCount(RoutingContext routingContext) {
        if (vertx.isClustered()) {
            // a bit hacky, but simple test
            routingContext
                    .response()
                    .setChunked(true)
                    .end(Integer.toString(((VertxImpl) vertx).getClusterManager().getNodes().size()));
        }
    }

    private void handleStartBenchmark(RoutingContext routingContext) {
        //funny cast due to codec registration
        eb.publish("control-feed", new SimpleBenchmark("test"));

        this.routingContext = routingContext;
    }

}
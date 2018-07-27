package io.sailrocket.distributed;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.VertxImpl;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
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
        //send response to calling client benchmark has finished
        eb.consumer("response-feed", message ->
                routingContext.response().putHeader("content-type", "application/json").end(message.body().toString())
        );
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
        eb.publish("control-feed", "start benchmark!");

        this.routingContext = routingContext;
    }
}
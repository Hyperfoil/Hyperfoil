package io.sailrocket.distributed;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class AgentControllerVerticle extends AbstractVerticle {

    private EventBus eb;

    @Override
    public void start() {

        eb = vertx.eventBus();

        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.get("/start").handler(this::handleStartBenchmark);

        vertx.createHttpServer().requestHandler(router::accept).listen(8090);
    }


    private void handleStartBenchmark(RoutingContext routingContext) {
        System.out.println("Starting distributed benchmark");

        eb.publish("control-feed", "start benchmark!");

        System.out.println("Sending response to caller");

        routingContext.response().putHeader("content-type", "application/json").end();
    }
}

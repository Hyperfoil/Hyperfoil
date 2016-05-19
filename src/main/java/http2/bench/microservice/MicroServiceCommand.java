package http2.bench.microservice;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.CommandBase;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class MicroServiceCommand extends CommandBase {

  @Parameter(names = "--port")
  public int port = 8080;

  @Parameter(names = "--sleep-time")
  public int sleepTime = 10;

  @Override
  public void run() throws Exception {
    Vertx vertx = Vertx.vertx();
    DeploymentOptions opts = new DeploymentOptions().
        setInstances(1).
        setConfig(new JsonObject().put("port", port).put("sleepTime", sleepTime));
    vertx.deployVerticle(Server.class.getName(), opts, ar -> {
      if (ar.succeeded()) {
        System.out.println("Micro Service started on port " + port);
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  public static class Server extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {
      int sleepTime = config().getInteger("sleepTime");
      int port = config().getInteger("port");
      HttpServer server = vertx.createHttpServer();
      server.requestHandler(req -> {
        req.endHandler(v -> {
          if (sleepTime > 0) {
            vertx.setTimer(sleepTime, timerID -> {
              req.response().end("Hello from microservice");
            });
          } else {
            req.response().end("Hello from microservice");
          }
        });
      });
      server.listen(port, ar -> {
        if (ar.succeeded()) {
          startFuture.complete();
        } else {
          startFuture.fail(ar.cause());
        }
      });
    }
  }

}

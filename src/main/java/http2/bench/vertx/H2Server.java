package http2.bench.vertx;

import http2.bench.Env;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class H2Server {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx(new VertxOptions().setInternalBlockingPoolSize(100));
    vertx.deployVerticle(ServerVerticle.JDK.class.getName(), new DeploymentOptions().setInstances(Env.numCore() * 2), ar -> {
      if (ar.succeeded()) {
        System.out.println("Server started");
      } else {
        ar.cause().printStackTrace();
      }
    });
  }
}

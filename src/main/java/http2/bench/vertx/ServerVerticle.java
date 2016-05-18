package http2.bench.vertx;

import http2.bench.Backend;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.SSLEngine;
import io.vertx.core.streams.Pump;

import java.util.UUID;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerVerticle extends AbstractVerticle {

  private SSLEngine engine;
  private Backend backend;
  private int acceptBacklog;

  public ServerVerticle() {
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    backend = Backend.valueOf(context.config().getString("backend"));
    acceptBacklog = context.config().getInteger("acceptBacklog");
    engine = SSLEngine.valueOf(config().getString("sslEngine"));

    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
        .setSsl(true)
        .setUseAlpn(true)
        .setHost("localhost")
        .setSslEngine(engine)
        .setAcceptBacklog(acceptBacklog)
        .addEnabledCipherSuite("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
        .setPort(config().getInteger("port"))
        .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert.pem")));

    FileSystem fs = vertx.fileSystem();
    if (!fs.existsBlocking("vertx.uploads")) {
      fs.mkdirsBlocking("vertx.uploads");
    }

    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        if (backend == Backend.DISK) {
          req.pause();
          String file = "vertx.uploads/" + UUID.randomUUID();
          fs.open(file, new OpenOptions().setCreate(true), ar1 -> {
            req.resume();
            if (ar1.succeeded()) {
              AsyncFile f = ar1.result();
              Pump pump = Pump.pump(req, f);
              pump.start();
              req.endHandler(v -> {
                f.close(ar2 -> fs.delete(file, ar3 -> {}));
                req.response().end("<html><body>Hello World</body></html>");
              });
            } else {
              ar1.cause().printStackTrace();
              req.response().setStatusCode(500).end();
            }
          });
        } else {
          req.handler(buff -> {});
          req.endHandler(v -> {
            req.response().end("<html><body>Hello World</body></html>");
          });
        }
      } else {
        req.response().end("<html><body>Hello World / " + req.version() + "</body></html>");
      }
    });

    server.listen(ar -> {
      if (ar.succeeded()) {
        startFuture.complete();
      } else {
        startFuture.fail(ar.cause());
      }
    });
  }
}

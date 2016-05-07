package http2.bench.vertx;

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

  private final SSLEngine engine;

  public ServerVerticle(SSLEngine engine) {
    this.engine = engine;
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
        .setSsl(true)
        .setUseAlpn(true)
        .setHost("localhost")
        .setSslEngine(engine)
        .addEnabledCipherSuite("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
        .setPort(8443)
        .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert.pem")));

    FileSystem fs = vertx.fileSystem();
    if (!fs.existsBlocking("vertx.uploads")) {
      fs.mkdirsBlocking("vertx.uploads");
    }

    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
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
        req.response().end("<html><body>Hello World</body></html>");
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

  public static class JDK extends ServerVerticle {
    public JDK() {
      super(SSLEngine.JDK);
    }
  }

  public static class OPENSSL extends ServerVerticle {
    public OPENSSL() {
      super(SSLEngine.OPENSSL);
    }
  }
}

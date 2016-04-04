package http2.bench.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.SSLEngine;

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

    server.requestHandler(req -> {
      req.response().end("<html><body>Hello World</body></html>");
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

package http2.bench.vertx;

import http2.bench.Backend;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.SSLEngine;
import io.vertx.core.streams.Pump;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLConnection;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerVerticle extends AbstractVerticle {

  private static final AtomicBoolean dbInitialized = new AtomicBoolean();

  private SSLEngine engine;
  private Backend backend;
  private int soAcceptBacklog;
  private int dbPoolSize;

  public ServerVerticle() {
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    backend = Backend.valueOf(context.config().getString("backend"));
    soAcceptBacklog = context.config().getInteger("soAcceptBacklog");
    engine = SSLEngine.valueOf(config().getString("sslEngine"));
    dbPoolSize = config().getInteger("dbPoolSize");

    Future<Void> dbFuture = Future.future();
    AsyncSQLClient client;
    if (backend == Backend.DB) {
      JsonObject postgreSQLClientConfig = new JsonObject().
          put("host", "localhost").
          put("maxPoolSize", dbPoolSize);
      client = PostgreSQLClient.createNonShared(vertx, postgreSQLClientConfig);
      if (dbInitialized.compareAndSet(false, true)) {
        client.getConnection(res -> {
          if (res.succeeded()) {
            SQLConnection conn = res.result();
            Future<Void> fut1 = Future.future();
            conn.execute("DROP TABLE IF EXISTS data_table", fut1.completer());
            fut1.compose(v -> {
              Future<Void> fut2 = Future.future();
              conn.execute("CREATE TABLE IF NOT EXISTS data_table (data json)", fut2.completer()).close();
              return fut2;
            }).setHandler(dbFuture.completer());
          } else {
            dbFuture.fail(res.cause());
          }
        });
      } else {
        dbFuture.complete();
      }
    } else {
      client = null;
      dbFuture.complete();
    }

    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
        .setSsl(true)
        .setUseAlpn(true)
        .setHost("localhost")
        .setSslEngine(engine)
        .setAcceptBacklog(soAcceptBacklog)
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
        if (backend == Backend.DB) {
          client.getConnection(res -> {
            if (res.succeeded()) {
              SQLConnection conn = res.result();
              conn.queryWithParams("INSERT INTO data_table (data) VALUES (?)", new JsonArray().add("{\"some\":\"json\"}"), ar -> {
                if (ar.succeeded()) {
                  req.response().end("<html><body>OK</body></html>");
                } else {
                  req.response().setStatusCode(500).end();
                }
                conn.close();
              });
            } else {
              req.response().setStatusCode(500).end();
            }
          });
        } else {
          req.response().end("<html><body>Hello World / " + req.version() + "</body></html>");
        }
      }
    });

    Future<HttpServer> serverInit = Future.future();
    server.listen(serverInit.completer());

    CompositeFuture.all(dbFuture, serverInit).<Void>map(c -> null).setHandler(startFuture.completer());
  }
}

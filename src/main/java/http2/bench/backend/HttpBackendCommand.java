package http2.bench.backend;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.CommandBase;
import http2.bench.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class HttpBackendCommand extends CommandBase {

  @Parameter(names = "--port")
  public int port = 8080;

  @Parameter(names = "--length", description = "the length in bytes")
  public String length = "0";

//  @Parameter(names = "--chunk-size", description = "the chunk size in bytes")
//  public int chunkSize = 1024;

  @Parameter(names = "--delay", description = "the delay in ms for sending the response")
  public long delay = 0;

  @Override
  public void run() throws Exception {
    Vertx vertx = Vertx.vertx();
    long length = Utils.parseSize(this.length).longValue();
    DeploymentOptions opts = new DeploymentOptions().
        setInstances(1).
        setConfig(new JsonObject()
            .put("port", port)
            .put("delay", delay)
            .put("length", length)
//            .put("chunkSize", chunkSize)
        );
    vertx.deployVerticle(Server.class.getName(), opts, ar -> {
      if (ar.succeeded()) {
        System.out.println("Micro Service started on port " + port);
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  public static class Server extends AbstractVerticle {

    private int length;
    private int port;
    private int delay;
//    private int chunkSize;
    private Buffer buffer;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
      delay = config().getInteger("delay");
      port = config().getInteger("port");
      length = config().getInteger("length");
//      chunkSize = config().getInteger("chunkSize");
      HttpServer server = vertx.createHttpServer();
      server.requestHandler(this::handle);
      server.listen(port, ar -> {
        if (ar.succeeded()) {
          startFuture.complete();
        } else {
          startFuture.fail(ar.cause());
        }
      });
      if (length > 0) {
        byte[] bytes = new byte[length];
        for (int i = 0;i < length;i++) {
          bytes[i] = (byte)('A' + (i % 26));
        }
        buffer = Buffer.buffer(bytes);
      }
    }

    protected void handle(HttpServerRequest req) {
      HttpServerResponse resp = req.response();
      if (delay > 0) {
        vertx.setTimer(delay, v -> {
          handleResp(resp);
        });
      } else {
        handleResp(resp);
      }
    }

    private void handleResp(HttpServerResponse resp) {
      if (buffer != null) {
/*
        resp.setChunked(true);
        SenderStream stream = new SenderStream(length, chunkSize);
        stream.endHandler(v -> {
          resp.end();
        });
        Pump pump = Pump.pump(stream, resp);
        pump.start();
        stream.send();
*/
        resp.end(buffer);
      } else {
        resp.end();
      }
    }
  }
}

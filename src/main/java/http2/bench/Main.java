package http2.bench;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import http2.bench.jetty.JettyServer;
import http2.bench.netty.NettyServer;
import http2.bench.undertow.UndertowServer;
import http2.bench.vertx.VertxServer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main {

  @Parameters()
  public static class MainCmd {
  }


  public static void main(String[] args) throws Exception {
    JCommander jc = new JCommander(new MainCmd());
    VertxServer vertx = new VertxServer();
    JettyServer jetty = new JettyServer();
    UndertowServer undertow = new UndertowServer();
    NettyServer netty = new NettyServer();
    jc.addCommand("vertx", vertx);
    jc.addCommand("jetty", jetty);
    jc.addCommand("undertow", undertow);
    jc.addCommand("netty", netty);
    jc.parse(args);
    String cmd = jc.getParsedCommand();
    ServerBase server = null;
    if (cmd != null) {
      switch (cmd) {
        case "vertx":
          server = vertx;
          break;
        case "jetty":
          server = jetty;
          break;
        case "undertow":
          server = undertow;
          break;
        case "netty":
          server = netty;
          break;
        default:
          break;
      }
    }
    if (server == null) {
      jc.usage();
    } else {
      if (server.help) {
        new JCommander(server).usage();
      } else {
        server.run();
      }
    }
  }
}

package http2.bench;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import http2.bench.jetty.JettyServerCommand;
import http2.bench.client.ClientCommand;
import http2.bench.netty.NettyServerCommand;
import http2.bench.undertow.UndertowServerCommand;
import http2.bench.vertx.VertxServerCommand;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main {

  @Parameters()
  public static class MainCmd {
  }

  public static void main(String[] args) throws Exception {
    JCommander jc = new JCommander(new MainCmd());
    VertxServerCommand vertx = new VertxServerCommand();
    JettyServerCommand jetty = new JettyServerCommand();
    UndertowServerCommand undertow = new UndertowServerCommand();
    NettyServerCommand netty = new NettyServerCommand();
    ClientCommand client = new ClientCommand();
    jc.addCommand("vertx", vertx);
    jc.addCommand("jetty", jetty);
    jc.addCommand("undertow", undertow);
    jc.addCommand("netty", netty);
    jc.addCommand("client", client);
    jc.parse(args);
    String cmd = jc.getParsedCommand();
    CommandBase command = null;
    if (cmd != null) {
      switch (cmd) {
        case "client":
          command = client;
          break;
        case "vertx":
          command = vertx;
          break;
        case "jetty":
          command = jetty;
          break;
        case "undertow":
          command = undertow;
          break;
        case "netty":
          command = netty;
          break;
        default:
          break;
      }
    }
    if (command == null) {
      jc.usage();
    } else {
      if (command.help) {
        new JCommander(command).usage();
      } else {

        // Integrate Java Flight Recorder
        // kill -s USR pid to start recording
        // kill -s INT pid to stop recording (or ctr-c)
/*
        AtomicReference<JavaFlightRecording> current = new AtomicReference<>();
        SignalHandler handler = signal -> {
          switch (signal.getName()) {
            case "INT": {
              JavaFlightRecording recording1 = current.getAndSet(null);
              if (recording1 != null) {
                System.out.println("Starting recording");
                recording1.stop();
              }
              System.exit(0);
              break;
            }
            case "USR2": {
              if (current.compareAndSet(null, JavaFlightRecording.builder().
                  withName(cmd.getClass().getSimpleName()).withOutputPath("/Users/julien/java/http2-bench/dump.jfr")
                  .build())) {
                System.out.println("Starting recording");
                current.get().start();
              }
              break;
            }
          }
        };
        Signal.handle(new Signal("USR2"), handler);
        Signal.handle(new Signal("INT"), handler);
*/

        command.run();
      }
    }
  }
}

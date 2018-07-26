package http2.bench;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import http2.bench.client.HttpClientCommand;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main {

  @Parameters()
  public static class MainCmd {
  }

  public static void main(String[] args) throws Exception {

    JCommander jc = new JCommander(new MainCmd());
    HttpClientCommand httpClient = new HttpClientCommand();
    jc.addCommand("http-client", httpClient);
    jc.parse(args);
    String cmd = jc.getParsedCommand();
    CommandBase command = null;
    if (cmd != null) {
      switch (cmd) {
        case "http-client":
          command = httpClient;
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

package http2.bench;

import http2.bench.client.HttpClientCommand;
import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandRuntime;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.registry.CommandRegistry;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main {

  public static void main(String[] args) throws Exception {

    CommandRuntime runtime = AeshCommandRuntimeBuilder
            .builder()
            .commandRegistry(httpClientCommandRegistry())
            .build();

    StringBuilder argsBuilder = new StringBuilder("http-clientPool").append(" ");
    for(String arg : args)
      argsBuilder.append(arg).append(" ");

    try {
      runtime.executeCommand(argsBuilder.toString());
    }
    catch (CommandNotFoundException e) {
      System.out.println("Command not found: "+argsBuilder.toString());

    }
    catch (Exception e) {
      e.printStackTrace();
    }

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

  }

  @SuppressWarnings("unchecked")
  private static CommandRegistry<HttpClientCommand, CommandInvocation> httpClientCommandRegistry() throws CommandLineParserException {
    return new AeshCommandRegistryBuilder()
            .command(HttpClientCommand.class)
            .create();
  }
}

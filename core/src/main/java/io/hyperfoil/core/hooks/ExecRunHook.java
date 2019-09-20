package io.hyperfoil.core.hooks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.RunHook;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ExecRunHook extends RunHook {
   private static final Logger log = LoggerFactory.getLogger(ExecRunHook.class);
   private final String command;

   public ExecRunHook(String name, String command) {
      super(name);
      this.command = command;
   }

   @Override
   public boolean run(String runId, Consumer<String> outputConsumer) {
      ProcessBuilder pb = new ProcessBuilder("sh", "-c", command).inheritIO()
            .redirectOutput(ProcessBuilder.Redirect.PIPE);
      pb.environment().put("RUN_ID", runId);
      try {
         log.info("{}: Starting command {}", name, command);
         Process process = pb.start();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
               log.trace(line);
               outputConsumer.accept(line);
               outputConsumer.accept("\n");
            }
         }
         return process.waitFor() == 0;
      } catch (IOException e) {
         log.error("Cannot start {}", e, name);
         return false;
      } catch (InterruptedException e) {
         log.error("Interrupted during hook execution", e);
         return false;
      }
   }

   public static class Builder implements RunHook.Builder {
      private String cmd;

      public Builder(String cmd) {
         this.cmd = cmd;
      }

      public Builder cmd(String cmd) {
         this.cmd = cmd;
         return this;
      }

      @Override
      public RunHook build(String name) {
         return new ExecRunHook(name, cmd);
      }
   }

   @MetaInfServices(RunHook.Factory.class)
   public static class BuilderFactory implements RunHook.Factory {
      @Override
      public String name() {
         return "exec";
      }

      @Override
      public boolean acceptsParam() {
         return true;
      }

      @Override
      public RunHook.Builder newBuilder(Locator locator, String param) {
         return new Builder(param);
      }
   }
}

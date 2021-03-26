package io.hyperfoil.core.hooks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.RunHook;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ExecRunHook extends RunHook {
   private static final Logger log = LogManager.getLogger(ExecRunHook.class);
   private final String command;

   public ExecRunHook(String name, String command) {
      super(name);
      this.command = command;
   }

   @Override
   public boolean run(Map<String, String> properties, Consumer<String> outputConsumer) {
      ProcessBuilder pb = new ProcessBuilder("sh", "-c", command).inheritIO()
            .redirectOutput(ProcessBuilder.Redirect.PIPE);
      pb.environment().putAll(properties);
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
         int exitValue = process.waitFor();
         log.info("{}: Completed command with exit code {}", name, exitValue);
         return exitValue == 0;
      } catch (IOException e) {
         log.error("Cannot start " + name, e);
         return false;
      } catch (InterruptedException e) {
         log.error("Interrupted during hook execution", e);
         return false;
      }
   }

   @MetaInfServices(RunHook.Builder.class)
   @Name("exec")
   public static class Builder implements RunHook.Builder, InitFromParam<Builder> {
      private String cmd;

      @Override
      public Builder init(String param) {
         this.cmd = param;
         return this;
      }

      @Override
      public RunHook build(String name) {
         return new ExecRunHook(name, cmd);
      }
   }
}

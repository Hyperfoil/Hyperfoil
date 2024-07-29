package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.ServiceLoader;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.io.FileResource;
import org.aesh.io.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.internal.Controller;
import io.hyperfoil.internal.Properties;

@CommandDefinition(name = "start-local", description = "Start non-clustered controller within the CLI process.")
public class StartLocal extends ServerCommand {
   @Option(shortName = 'l', description = "Default log level for controller log.", defaultValue = "")
   private String logLevel;

   @Option(shortName = 'q', description = "Do not print anything on output in this command.", hasValue = false)
   private boolean quiet;

   @Argument(description = "Root directory used for the controller.")
   private Resource rootDir;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      HyperfoilCliContext ctx = invocation.context();
      if (ctx.localControllerHost() != null || ctx.localControllerPort() > 0) {
         if (!quiet) {
            invocation.warn("Local controller is already running, not starting.");
         }
      } else {
         Controller.Factory factory = null;
         for (Controller.Factory f : ServiceLoader.load(Controller.Factory.class)) {
            factory = f;
            break;
         }
         if (factory == null) {
            throw new CommandException("Controller is not on the classpath, cannot start.");
         }
         rootDir = CliUtil.sanitize(rootDir);
         if (rootDir != null && rootDir.exists() && !(rootDir.isDirectory() && rootDir instanceof FileResource)) {
            if (!quiet) {
               invocation.println("You are trying to start Hyperfoil controller with root dir " + rootDir);
            }
            throw new CommandException(rootDir + " exists but it is not a directory");
         }
         if (!quiet) {
            invocation.println("Starting controller in "
                  + (rootDir == null ? "default directory (/tmp/hyperfoil)" : rootDir.getAbsolutePath()));
         }
         // disable logs from controller
         if (!logLevel.isEmpty()) {
            System.setProperty(Properties.CONTROLLER_LOG_LEVEL, logLevel);
         }
         reconfigureLogging(invocation);
         Controller controller = factory.start(rootDir == null ? null : ((FileResource) rootDir).getFile().toPath());
         ctx.setLocalControllerHost(controller.host());
         ctx.setLocalControllerPort(controller.port());
         if (!quiet) {
            invocation.println("Controller started, listening on " + controller.host() + ":" + controller.port());
         }
         ctx.addCleanup(controller::stop);
      }
      if (!quiet) {
         invocation.println("Connecting to the controller...");
      }
      if (ctx.client() != null) {
         ctx.client().close();
      }
      connect(invocation, quiet, ctx.localControllerHost(), ctx.localControllerPort(), false, false, null);
      return CommandResult.SUCCESS;
   }

   private void reconfigureLogging(HyperfoilCommandInvocation invocation) {
      try {
         LoggerContext context = ((Logger) LogManager.getLogger(getClass())).getContext();
         InputStream configStream = getClass().getClassLoader().getResourceAsStream("log4j2-local-controller.xml");
         context.setConfiguration(new XmlConfiguration(context, new ConfigurationSource(configStream)));
      } catch (IOException e) {
         invocation.error("Failed to set logger configuration");
         invocation.error(e);
      }
   }
}

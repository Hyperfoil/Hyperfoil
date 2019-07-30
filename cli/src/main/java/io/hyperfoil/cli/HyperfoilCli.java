/*
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.hyperfoil.cli;

import io.hyperfoil.cli.commands.Connect;
import io.hyperfoil.cli.commands.Edit;
import io.hyperfoil.cli.commands.Info;
import io.hyperfoil.cli.commands.Kill;
import io.hyperfoil.cli.commands.Log;
import io.hyperfoil.cli.commands.Run;
import io.hyperfoil.cli.commands.RunLocal;
import io.hyperfoil.cli.commands.Sessions;
import io.hyperfoil.cli.commands.Stats;
import io.hyperfoil.cli.commands.Status;
import io.hyperfoil.cli.commands.Upload;
import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.cli.context.HyperfoilCommandInvocationProvider;
import io.hyperfoil.cli.context.HyperfoilCompleterData;

import org.aesh.AeshConsoleRunner;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.activator.CommandActivator;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.option.Option;
import org.aesh.command.registry.CommandRegistryException;
import org.aesh.command.settings.Settings;
import org.aesh.command.settings.SettingsBuilder;
import org.aesh.command.validator.ValidatorInvocation;
import org.aesh.readline.Prompt;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;

public class HyperfoilCli {

       //ignore logging when running in the console below severe
   static {
      Handler[] handlers =
              Logger.getLogger( "" ).getHandlers();
      for ( int index = 0; index < handlers.length; index++ ) {
         handlers[index].setLevel( Level.SEVERE);
      }
   }


   public static void main(String[] args) throws IOException, CommandRegistryException {

       //set logger impl
       System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");

      HyperfoilCliContext context = new HyperfoilCliContext();
      Settings<HyperfoilCommandInvocation, ConverterInvocation, CompleterInvocation, ValidatorInvocation,
                       OptionActivator, CommandActivator> settings =
               SettingsBuilder.<HyperfoilCommandInvocation, ConverterInvocation, CompleterInvocation,
                                       ValidatorInvocation, OptionActivator, CommandActivator>builder()
                       .logging(true)
                       .enableMan(false)
                       .enableAlias(false)
                       .enableExport(false)
                       .enableSearchInPaging(true)
                       .readInputrc(true)
                       .commandRegistry(
                               AeshCommandRegistryBuilder.<HyperfoilCommandInvocation>builder()
                                       .command(Connect.class)
                                       .command(Edit.class)
                                       .command(ExitCommand.class)
                                       .command(Info.class)
                                       .command(Kill.class)
                                       .command(Log.class)
                                       .command(RunLocal.class)
                                       .command(Run.class)
                                       .command(Sessions.class)
                                       .command(Stats.class)
                                       .command(Status.class)
                                       .command(Upload.class)
                                       .command(Wrk.WrkCommand.class)
                                       .create())
                       .commandInvocationProvider(new HyperfoilCommandInvocationProvider(context))
                       .completerInvocationProvider(completerInvocation -> new HyperfoilCompleterData(completerInvocation, context))
                       .build();

       AeshConsoleRunner runner = AeshConsoleRunner.builder().settings(settings);
       runner.prompt(new Prompt(new TerminalString("[hyperfoil]$ ",
                        new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT))));

        runner.start();
    }

    @CommandDefinition(name = "exit", description = "exit the program", aliases = {"quit"})
    public static class ExitCommand implements Command<HyperfoilCommandInvocation> {

       @Option(shortName = 'f', hasValue = false)
       private boolean force;

        @Override
        public CommandResult execute(HyperfoilCommandInvocation invocation) {
            if (invocation.context().running() && !force) {
               invocation.println("Benchmark " + invocation.context().benchmark().name() +
                     " is currently running, not possible to cleanly exit. To force an exit, use --force");
            } else {
               invocation.stop();
            }

            if (invocation.context().client() != null) {
               invocation.context().client().close();
            }
            return CommandResult.SUCCESS;
        }
    }

}


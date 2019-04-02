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

import io.hyperfoil.cli.commands.RunLocal;
import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.cli.context.HyperfoilCommandInvocationProvider;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.command.settings.Settings;
import org.aesh.command.settings.SettingsBuilder;
import org.aesh.readline.Prompt;
import org.aesh.readline.ReadlineConsole;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HyperfoilCli {

       //ignore logging when running in the console below severe
   static {
      Handler[] handlers =
              Logger.getLogger( "" ).getHandlers();
      for ( int index = 0; index < handlers.length; index++ ) {
         handlers[index].setLevel( Level.SEVERE);
      }
   }


   public static void main(String[] args) throws CommandLineParserException, IOException {

       //set logger impl
       System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");

       HyperfoilCliContext context = new HyperfoilCliContext();

       SettingsBuilder builder = SettingsBuilder.builder()
           .logging(true)
           .enableMan(false)
           .enableAlias(false)
           .enableExport(false)
           .enableSearchInPaging(true)
           .readInputrc(true);

       CommandRegistry registry = new AeshCommandRegistryBuilder()
              .command(ExitCommand.class)
              .command(Wrk.WrkCommand.class)
              .command(RunLocal.class)
              .create();

       Settings settings = builder
              .commandRegistry(registry)
              .commandInvocationProvider(new HyperfoilCommandInvocationProvider(context))
              .build();

       ReadlineConsole console = new ReadlineConsole(settings);
       console.setPrompt(new Prompt(new TerminalString("[hyperfoil@localhost]$ ",
                        new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT))));

        console.start();
    }

    @CommandDefinition(name = "exit", description = "exit the program", aliases = {"quit"})
    public static class ExitCommand implements Command<HyperfoilCommandInvocation> {

        @Override
        public CommandResult execute(HyperfoilCommandInvocation invocation) {
            if(invocation.context().running())
                invocation.println("Benchmark "+invocation.context().benchmark().name()+
                                           " is currently running, not possible to exit");

            else
                invocation.stop();
            return CommandResult.SUCCESS;
        }
    }

}


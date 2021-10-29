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

import io.hyperfoil.cli.commands.Compare;
import io.hyperfoil.cli.commands.Connect;
import io.hyperfoil.cli.commands.Connections;
import io.hyperfoil.cli.commands.Cpu;
import io.hyperfoil.cli.commands.Edit;
import io.hyperfoil.cli.commands.Exit;
import io.hyperfoil.cli.commands.Export;
import io.hyperfoil.cli.commands.Help;
import io.hyperfoil.cli.commands.Info;
import io.hyperfoil.cli.commands.Inspect;
import io.hyperfoil.cli.commands.Kill;
import io.hyperfoil.cli.commands.Load;
import io.hyperfoil.cli.commands.Oc;
import io.hyperfoil.cli.commands.Log;
import io.hyperfoil.cli.commands.Report;
import io.hyperfoil.cli.commands.Run;
import io.hyperfoil.cli.commands.RunLocal;
import io.hyperfoil.cli.commands.Runs;
import io.hyperfoil.cli.commands.Sessions;
import io.hyperfoil.cli.commands.Shutdown;
import io.hyperfoil.cli.commands.StartLocal;
import io.hyperfoil.cli.commands.Stats;
import io.hyperfoil.cli.commands.Status;
import io.hyperfoil.cli.commands.Upload;
import io.hyperfoil.cli.commands.Version;
import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.cli.commands.Wrk2;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.cli.context.HyperfoilCommandInvocationProvider;
import io.hyperfoil.cli.context.HyperfoilCompleterData;
import io.hyperfoil.impl.Util;

import org.aesh.AeshConsoleRunner;
import org.aesh.command.Command;
import org.aesh.command.activator.CommandActivator;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.registry.CommandRegistryException;
import org.aesh.command.settings.Settings;
import org.aesh.command.settings.SettingsBuilder;
import org.aesh.command.validator.ValidatorInvocation;
import org.aesh.readline.Prompt;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HyperfoilCli {

   public static final String CLI_PROMPT = "CLI_PROMPT";

   //ignore logging when running in the console below severe
   static {
      Handler[] handlers = Logger.getLogger("").getHandlers();
      for (int index = 0; index < handlers.length; index++) {
         handlers[index].setLevel(Level.SEVERE);
      }
   }

   public static void main(String[] args) throws CommandRegistryException {
      new HyperfoilCli().run();
   }

   public void run() throws CommandRegistryException {
      HyperfoilCliContext context = new HyperfoilCliContext();
      AeshConsoleRunner runner = configureRunner(context, settingsBuilder(context).build(), System.getenv(CLI_PROMPT));

      CompletableFuture<List<String>> endpoints = suggestedEndpoints();
      endpoints.whenComplete((list, e) -> {
         if (e == null && !list.isEmpty()) {
            context.setSuggestedControllerHosts(list);
         }
      });

      runner.start();
   }

   protected AeshConsoleRunner configureRunner(HyperfoilCliContext context, Settings<HyperfoilCommandInvocation, ConverterInvocation, CompleterInvocation, ValidatorInvocation<?, ?>, OptionActivator, CommandActivator> settings, String cliPrompt) {
      context.commandRegistry(settings.commandRegistry());

      AeshConsoleRunner runner = AeshConsoleRunner.builder().settings(settings);
      if (cliPrompt == null) {
         runner.prompt(new Prompt(new TerminalString("[hyperfoil]$ ",
               new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT))));
      } else {
         runner.prompt(new Prompt(cliPrompt));
      }
      return runner;
   }

   protected SettingsBuilder<HyperfoilCommandInvocation, ConverterInvocation, CompleterInvocation, ValidatorInvocation<?, ?>, OptionActivator, CommandActivator> settingsBuilder(HyperfoilCliContext context) throws CommandRegistryException {
      AeshCommandRegistryBuilder<HyperfoilCommandInvocation> commandRegistryBuilder = AeshCommandRegistryBuilder.builder();
      for (Class<? extends Command> command : getCommands()) {
         commandRegistryBuilder.command(command);
      }
      return SettingsBuilder.<HyperfoilCommandInvocation, ConverterInvocation, CompleterInvocation,
            ValidatorInvocation<?, ?>, OptionActivator, CommandActivator>builder()
            .logging(true)
            .enableMan(false)
            .enableAlias(false)
            .enableExport(false)
            .enableSearchInPaging(true)
            .readInputrc(true)
            .commandRegistry(commandRegistryBuilder.create())
            .commandInvocationProvider(new HyperfoilCommandInvocationProvider(context))
            .completerInvocationProvider(completerInvocation -> new HyperfoilCompleterData(completerInvocation, context))
            .setConnectionClosedHandler(nil -> context.stop());
   }

   protected List<Class<? extends Command>> getCommands() {
      return Arrays.asList(
            Connect.class,
            Connections.class,
            Compare.class,
            Cpu.class,
            Edit.class,
            Exit.class,
            Export.class,
            Help.class,
            Info.class,
            Inspect.class,
            Kill.class,
            Load.class,
            Log.class,
            Oc.class,
            Report.class,
            RunLocal.class,
            Run.class,
            Runs.class,
            Sessions.class,
            Shutdown.class,
            StartLocal.class,
            Stats.class,
            Status.class,
            Upload.class,
            Version.class,
            Wrk.WrkCommand.class,
            Wrk2.Wrk2Command.class
      );
   }

   private static CompletableFuture<List<String>> suggestedEndpoints() {
      CompletableFuture<List<String>> openshiftPorts;
      try {
         Process start = new ProcessBuilder("oc", "get", "route", "-A", "-l", "hyperfoil", "-o", "jsonpath={range .items[*]}{.spec.tls.termination}:{.status.ingress[0].host} ").start();
         openshiftPorts = CompletableFuture.supplyAsync(() -> {
            try {
               return Stream.of(Util.toString(start.getInputStream()).split("[ \\n\\t]+"))
                     .map(endpoint -> {
                        int index = endpoint.indexOf(':');
                        String prefix = index == 0 ? "http://" : "https://";
                        return prefix + endpoint.substring(index + 1);
                     }).collect(Collectors.toList());
            } catch (IOException e) {
               return Collections.emptyList();
            }
         });
      } catch (IOException e) {
         openshiftPorts = CompletableFuture.completedFuture(Collections.emptyList());
      }
      return openshiftPorts.thenApply(list -> {
         if (CliUtil.isPortListening("localhost", 8090)) {
            ArrayList<String> copy = new ArrayList<>(list);
            copy.add("localhost:8090");
            return copy;
         } else {
            return list;
         }
      });
   }


}


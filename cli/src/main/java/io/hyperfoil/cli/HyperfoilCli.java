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
import io.hyperfoil.cli.commands.Edit;
import io.hyperfoil.cli.commands.Exit;
import io.hyperfoil.cli.commands.Export;
import io.hyperfoil.cli.commands.Help;
import io.hyperfoil.cli.commands.Info;
import io.hyperfoil.cli.commands.Inspect;
import io.hyperfoil.cli.commands.Kill;
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

import org.aesh.AeshConsoleRunner;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;

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

      //set logger impl
      System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");

      HyperfoilCliContext context = new HyperfoilCliContext();
      Settings<HyperfoilCommandInvocation, ConverterInvocation, CompleterInvocation, ValidatorInvocation<?, ?>,
            OptionActivator, CommandActivator> settings =
            SettingsBuilder.<HyperfoilCommandInvocation, ConverterInvocation, CompleterInvocation,
                  ValidatorInvocation<?, ?>, OptionActivator, CommandActivator>builder()
                  .logging(true)
                  .enableMan(false)
                  .enableAlias(false)
                  .enableExport(false)
                  .enableSearchInPaging(true)
                  .readInputrc(true)
                  .commandRegistry(
                        AeshCommandRegistryBuilder.<HyperfoilCommandInvocation>builder()
                              .command(Connect.class)
                              .command(Compare.class)
                              .command(Edit.class)
                              .command(Exit.class)
                              .command(Export.class)
                              .command(Help.class)
                              .command(Info.class)
                              .command(Inspect.class)
                              .command(Kill.class)
                              .command(Log.class)
                              .command(Oc.class)
                              .command(Report.class)
                              .command(RunLocal.class)
                              .command(Run.class)
                              .command(Runs.class)
                              .command(Sessions.class)
                              .command(Shutdown.class)
                              .command(StartLocal.class)
                              .command(Stats.class)
                              .command(Status.class)
                              .command(Upload.class)
                              .command(Version.class)
                              .command(Wrk.WrkCommand.class)
                              .command(Wrk2.Wrk2Command.class)
                              .create())
                  .commandInvocationProvider(new HyperfoilCommandInvocationProvider(context))
                  .completerInvocationProvider(completerInvocation -> new HyperfoilCompleterData(completerInvocation, context))
                  .setConnectionClosedHandler(nil -> context.stop())
                  .build();
      context.commandRegistry(settings.commandRegistry());

      AeshConsoleRunner runner = AeshConsoleRunner.builder().settings(settings);
      String cliPrompt = System.getenv(CLI_PROMPT);
      if (cliPrompt == null) {
         runner.prompt(new Prompt(new TerminalString("[hyperfoil]$ ",
               new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT))));
      } else {
         runner.prompt(new Prompt(cliPrompt));
      }

      CompletableFuture<List<String>> endpoints = suggestedEndpoints();
      endpoints.whenComplete((list, e) -> {
         if (e == null && !list.isEmpty()) {
            context.setSuggestedControllerHosts(list);
         }
      });

      runner.start();
   }

   private static CompletableFuture<List<String>> suggestedEndpoints() {
      CompletableFuture<List<String>> openshiftPorts;
      try {
         Process start = new ProcessBuilder("oc", "get", "route", "-A", "-l", "hyperfoil", "-o", "jsonpath={range .items[*]}{.spec.tls.termination}:{.status.ingress[0].host} ").start();
         openshiftPorts = CompletableFuture.supplyAsync(() -> {
            try {
               return Stream.of(io.hyperfoil.util.Util.toString(start.getInputStream()).split("[ \\n\\t]+"))
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
         if (Util.isPortListening("localhost", 8090)) {
            ArrayList<String> copy = new ArrayList<>(list);
            copy.add("localhost:8090");
            return copy;
         } else {
            return list;
         }
      });
   }


}


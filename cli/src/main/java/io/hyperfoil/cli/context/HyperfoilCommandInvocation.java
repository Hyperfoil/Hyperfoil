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

package io.hyperfoil.cli.context;

import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.Executor;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.shell.Shell;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.readline.Prompt;
import org.aesh.readline.action.KeyAction;
import org.aesh.terminal.utils.ANSI;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.cli.HyperfoilCli;
import io.hyperfoil.cli.commands.ServerCommand;
import io.hyperfoil.impl.Util;

public class HyperfoilCommandInvocation implements CommandInvocation {

   private final CommandInvocation commandInvocation;
   private final HyperfoilCliContext context;

   protected HyperfoilCommandInvocation(HyperfoilCliContext context, CommandInvocation commandInvocation) {
      this.context = context;
      this.commandInvocation = commandInvocation;
   }

   public HyperfoilCliContext context() {
      return context;
   }

   @Override
   public Shell getShell() {
      return commandInvocation.getShell();
   }

   @Override
   public void setPrompt(Prompt prompt) {
      if (System.getenv(HyperfoilCli.CLI_PROMPT) == null) {
         commandInvocation.setPrompt(prompt);
      }
   }

   @Override
   public Prompt getPrompt() {
      return commandInvocation.getPrompt();
   }

   @Override
   public String getHelpInfo(String commandName) {
      return commandInvocation.getHelpInfo(commandName);
   }

   @Override
   public String getHelpInfo() {
      return commandInvocation.getHelpInfo();
   }

   @Override
   public void stop() {
      context.stop();
      commandInvocation.stop();
   }

   @Override
   public KeyAction input() throws InterruptedException {
      return commandInvocation.input();
   }

   @Override
   public KeyAction input(long timeout, TimeUnit unit) throws InterruptedException {
      return commandInvocation.input(timeout, unit);
   }

   @Override
   public String inputLine() throws InterruptedException {
      return commandInvocation.inputLine();
   }

   @Override
   public String inputLine(Prompt prompt) throws InterruptedException {
      return commandInvocation.inputLine(prompt);
   }

   @Override
   public void executeCommand(String input) throws CommandNotFoundException,
         CommandLineParserException, OptionValidatorException,
         CommandValidatorException, CommandException, InterruptedException, IOException {
      commandInvocation.executeCommand(input);
   }

   @Override
   public void print(String msg, boolean paging) {
      commandInvocation.print(msg, paging);
   }

   @Override
   public void println(String msg, boolean paging) {
      commandInvocation.println(msg, paging);
   }

   @Override
   public Executor<? extends CommandInvocation> buildExecutor(String line) throws CommandNotFoundException,
         CommandLineParserException, OptionValidatorException, CommandValidatorException, IOException {
      return commandInvocation.buildExecutor(line);
   }

   @Override
   public CommandInvocationConfiguration getConfiguration() {
      return commandInvocation.getConfiguration();
   }

   public void warn(String message) {
      println(ANSI.YELLOW_TEXT + "WARNING: " + message + ANSI.RESET);
   }

   public void error(String message) {
      println(ANSI.RED_TEXT + "ERROR: " + message + ANSI.RESET);
   }

   public void error(Throwable t) {
      error(Util.explainCauses(t));
   }

   public void error(String message, Throwable t) {
      error(message + ": " + Util.explainCauses(t));
   }

   public void printStackTrace(Throwable t) {
      print(ANSI.RED_TEXT);
      printStackTrace(t, new HashSet<>());
      print(ANSI.RESET);
   }

   private void printStackTrace(Throwable t, HashSet<Throwable> set) {
      if (!set.add(t)) {
         println("[CIRCULAR REFERENCE]");
         return;
      }
      for (StackTraceElement traceElement : t.getStackTrace()) {
         println("\tat " + traceElement);
      }
      for (Throwable se : t.getSuppressed()) {
         println("SUPPRESSED: " + se.getMessage());
         printStackTrace(se, set);
      }
      Throwable cause = t.getCause();
      if (cause != null) {
         println("CAUSED BY: " + cause.getMessage());
         printStackTrace(cause, set);
      }
   }

   public void executeSwitchable(String input) throws CommandException {
      context.setSwitchable(true);
      try {
         while (true) {
            try {
               executeCommand(input);
               break;
            } catch (RuntimeException e) {
               Throwable cause = e.getCause();
               while (cause instanceof RuntimeException && cause != cause.getCause() && !(cause instanceof ServerCommand.SwitchCommandException)) {
                  cause = cause.getCause();
               }
               if (cause instanceof ServerCommand.SwitchCommandException) {
                  input = ((ServerCommand.SwitchCommandException) cause).newCommand;
               } else {
                  error(e);
                  throw new CommandException(e);
               }
            } catch (Exception e) {
               error(e);
               throw new CommandException(e);
            }
         }
      } finally {
         context.setSwitchable(false);
      }
   }
}

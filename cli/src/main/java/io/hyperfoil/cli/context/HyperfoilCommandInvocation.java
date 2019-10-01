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

import java.io.IOException;

public class HyperfoilCommandInvocation implements CommandInvocation {

   private final CommandInvocation commandInvocation;
   private final HyperfoilCliContext context;

   HyperfoilCommandInvocation(HyperfoilCliContext context, CommandInvocation commandInvocation) {
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
      commandInvocation.setPrompt(prompt);
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
      commandInvocation.stop();
   }

   @Override
   public KeyAction input() throws InterruptedException {
      return commandInvocation.input();
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

   @SuppressWarnings("unchecked")
   @Override
   public Executor<? extends CommandInvocation> buildExecutor(String line) throws CommandNotFoundException,
         CommandLineParserException, OptionValidatorException, CommandValidatorException, IOException {
      return commandInvocation.buildExecutor(line);
   }

   @Override
   public CommandInvocationConfiguration getConfiguration() {
      return commandInvocation.getConfiguration();
   }
}

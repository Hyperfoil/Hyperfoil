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

package io.hyperfoil.cli.commands;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.io.Resource;
import org.aesh.utils.ANSI;

@CommandDefinition(name = "run-local", description = "Deprecated. Used to start a run within the same VM.")
public class RunLocal implements Command<CommandInvocation> {
   @Option(shortName = 'h', hasValue = false, overrideRequired = true)
   boolean help;

   @Argument(description = "Yaml file that should be parsed")
   Resource yaml;

   @Override
   public CommandResult execute(CommandInvocation invocation) {
      invocation.println("This command has been deprected in favor of " + bold("start-local") + " command.");
      invocation.println(bold("start-local") + " starts Hyperfoil controller server inside the CLI, so you should then "
            + bold("upload") + " the benchmark and " + bold("run") + " it.");
      return CommandResult.SUCCESS;
   }

   private String bold(String s) {
      return ANSI.BOLD + s + ANSI.BOLD_OFF;
   }
}

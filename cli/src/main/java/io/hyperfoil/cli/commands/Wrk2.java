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
import org.aesh.command.option.Option;

import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public class Wrk2 extends WrkAbstract {

   private static final String CMD = "wrk2";

   public static void main(String[] args) {
      Wrk2 wrk = new Wrk2();
      wrk.exec(args);
   }

   @Override
   protected String getCommandName() {
      return CMD;
   }

   @Override
   protected Class<? extends Command<HyperfoilCommandInvocation>> getCommand() {
      return Wrk2Command.class;
   }

   @CommandDefinition(name = CMD, description = "Runs a workload simulation against one endpoint using the same vm")
   public class Wrk2Command extends WrkAbstract.AbstractWrkCommand {

      @Option(shortName = 'R', description = "Work rate (throughput)", required = true)
      int rate;

      @Override
      protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, WrkScenario.PhaseType phaseType, long durationMs) {
         return WrkScenarioPhaseConfig.wrk2PhaseConfig(catalog, phaseType, durationMs, rate);
      }
   }
}

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

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;


public class Wrk2 extends WrkAbstract {

   private static final String CMD = "wrk2";

   public static void main(String[] args) {
      Wrk2 wrk = new Wrk2();
      wrk.mainMethod(args, Wrk2.Wrk2Command.class);
   }

   @Override
   protected String getCommand() {
      return CMD;
   }

   @CommandDefinition(name = CMD, description = "Runs a workload simluation against one endpoint using the same vm")
   public class Wrk2Command extends WrkAbstract.AbstractWrkCommand {

      @Option(shortName = 'R', description = "Work rate (throughput)", required = true)
      int rate;

      @Override
      protected PhaseBuilder<?> rootPhase(BenchmarkBuilder benchmarkBuilder, String phase) {
         return benchmarkBuilder.addPhase(phase).constantPerSec(rate)
               .maxSessions(rate * 15);
      }

   }

}

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

import io.hyperfoil.api.config.PhaseBuilder;

import org.aesh.command.CommandDefinition;


public class Wrk extends WrkAbstract {

   private static final String CMD = "wrk";

   public static void main(String[] args) {
      Wrk wrk = new Wrk();
      wrk.mainMethod(args, Wrk.WrkCommand.class);
   }

   @Override
   protected String getCommand() {
      return CMD;
   }

   @CommandDefinition(name = CMD, description = "Runs a workload simluation against one endpoint using the same vm")
   public class WrkCommand extends WrkAbstract.AbstractWrkCommand {

      @Override
      protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog) {
         return catalog.always(threads).users(threads); //set number of users to number of threads, same threading model as wrk
      }
   }

}

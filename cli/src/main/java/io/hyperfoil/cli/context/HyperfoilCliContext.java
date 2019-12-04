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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.aesh.command.CommandException;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClient;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HyperfoilCliContext {
   private Benchmark benchmark;
   private RestClient client;
   private Client.BenchmarkRef serverBenchmark;
   private Client.RunRef serverRun;
   private Map<String, String> logFiles = new HashMap<>();
   private Map<String, String> logIds = new HashMap<>();
   private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "CLI-scheduled-executor");
      thread.setDaemon(true);
      return thread;
   });
   private String controllerId;
   private ScheduledFuture<?> controllerPollTask;
   private String localControllerHost = null;
   private int localControllerPort = -1;
   private List<Runnable> cleanup = new ArrayList<>();

   public HyperfoilCliContext() {
   }

   /**
    * @return the current running benchmark instance
    */
   public Benchmark benchmark() {
      return benchmark;
   }

   public void setBenchmark(Benchmark benchmark) {
      this.benchmark = benchmark;
   }

   public RestClient client() {
      return client;
   }

   public void setClient(RestClient client) {
      this.client = client;
   }

   public void setServerBenchmark(Client.BenchmarkRef ref) {
      this.serverBenchmark = ref;
   }

   public Client.BenchmarkRef serverBenchmark() {
      return serverBenchmark;
   }

   public void setServerRun(Client.RunRef ref) {
      serverRun = ref;
   }

   public Client.RunRef serverRun() {
      return serverRun;
   }

   public String getLogFile(String node) {
      return logFiles.get(node);
   }

   public String getLogId(String node) {
      return logIds.get(node);
   }

   public void addLog(String node, String file, String id) throws CommandException {
      if (logFiles.containsKey(node) || logIds.containsKey(node)) {
         throw new CommandException("Log file for " + node + " already present");
      }
      logFiles.put(node, file);
      logIds.put(node, id);
   }

   public void updateLogId(String node, String logId) {
      logIds.put(node, logId);
   }

   public ScheduledExecutorService executor() {
      return executor;
   }

   public String controllerId() {
      return controllerId;
   }

   public void setControllerId(String id) {
      controllerId = id;
   }

   public void setControllerPollTask(ScheduledFuture<?> future) {
      if (controllerPollTask != null) {
         controllerPollTask.cancel(false);
      }
      controllerPollTask = future;
   }

   public String localControllerHost() {
      return localControllerHost;
   }

   public void setLocalControllerHost(String localControllerHost) {
      this.localControllerHost = localControllerHost;
   }

   public int localControllerPort() {
      return localControllerPort;
   }

   public void setLocalControllerPort(int localControllerPort) {
      this.localControllerPort = localControllerPort;
   }

   public void addCleanup(Runnable runnable) {
      cleanup.add(runnable);
   }

   public void stop() {
      if (client != null) {
         client.close();
         client = null;
      }
      for (Runnable c : cleanup) {
         c.run();
      }
   }
}

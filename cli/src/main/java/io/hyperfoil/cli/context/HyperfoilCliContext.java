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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.aesh.command.CommandException;
import org.aesh.command.registry.CommandRegistry;

import io.hyperfoil.cli.Pager;
import io.hyperfoil.cli.ProcessPager;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.controller.model.Run;
import io.vertx.core.Vertx;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HyperfoilCliContext {
   private final Vertx vertx;
   private final boolean providedVertx;
   private RestClient client;
   private Client.BenchmarkRef serverBenchmark;
   private Map<String, String> currentParams = Collections.emptyMap();
   private Client.RunRef serverRun;
   private Map<String, File> logFiles = new HashMap<>();
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
   // We'll start with online set to true to not say 'we're back online' when connecting the first time
   private boolean online = true;
   private CommandRegistry<? extends HyperfoilCommandInvocation> commandRegistry;
   private List<String> suggestedControllerHosts = Collections.emptyList();
   private boolean switchable;

   public HyperfoilCliContext() {
      this(Vertx.vertx(), false);
   }

   protected HyperfoilCliContext(Vertx vertx, boolean providedVertx) {
      this.vertx = vertx;
      this.providedVertx = providedVertx;
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

   public void setCurrentParams(Map<String, String> currentParams) {
      this.currentParams = currentParams;
   }

   public Map<String, String> currentParams() {
      return currentParams;
   }

   public void setServerRun(Client.RunRef ref) {
      serverRun = ref;
   }

   public Client.RunRef serverRun() {
      return serverRun;
   }

   public File getLogFile(String node) {
      return logFiles.get(node);
   }

   public String getLogId(String node) {
      return logIds.get(node);
   }

   public void addLog(String node, File file, String id) throws CommandException {
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
      executor.shutdown();
      try {
         executor.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      if (client != null) {
         client.close();
         client = null;
      }
      for (Runnable c : cleanup) {
         c.run();
      }
      if (!providedVertx) {
         vertx.close();
      }
   }

   public void setOnline(boolean online) {
      this.online = online;
   }

   public boolean online() {
      return online;
   }

   public void commandRegistry(CommandRegistry<? extends HyperfoilCommandInvocation> commandRegistry) {
      this.commandRegistry = commandRegistry;
   }

   public CommandRegistry<? extends HyperfoilCommandInvocation> commandRegistry() {
      return commandRegistry;
   }

   public Vertx vertx() {
      return vertx;
   }

   public synchronized List<String> suggestedControllerHosts() {
      return suggestedControllerHosts;
   }

   public synchronized void setSuggestedControllerHosts(List<String> suggestedControllerHosts) {
      this.suggestedControllerHosts = suggestedControllerHosts;
   }

   public String interruptKey() {
      return "Ctrl+C";
   }

   public Pager createPager(String pager) {
      return new ProcessPager(pager);
   }

   public synchronized void notifyRunCompleted(Run run) {
      // not implemented in regular CLI
   }

   public void setSwitchable(boolean switchable) {
      this.switchable = switchable;
   }

   public boolean isSwitchable() {
      return this.switchable;
   }
}

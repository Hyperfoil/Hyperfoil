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

import java.util.HashMap;
import java.util.Map;

import org.aesh.command.CommandException;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClient;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HyperfoilCliContext {
    private Benchmark benchmark;
    private boolean running;
    private RestClient client;
    private Client.BenchmarkRef serverBenchmark;
    private Client.RunRef serverRun;
    private Map<String, String> logFiles = new HashMap<>();
    private Map<String, String> logIds = new HashMap<>();

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

    public boolean running() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
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
}

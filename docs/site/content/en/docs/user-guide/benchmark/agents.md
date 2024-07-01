---
title: Agents
description: Entities responsible for executing benchmark and collecting statistics
categories: [Guide, Benchmark]
tags: [guides, benchmark, agents]
weight: 1
---

This section can be omitted in [standalone mode](/docs/user-guide/installation/start_manual/).

Agents section forms either a list or map with arbitrary agent names and either an inline or properties-style definition:

```yaml
agents:
  someAgent: "inline definition"
  otherAgent:
    foo: bar
```

The definition is passed to an instance of `i.h.api.deployment.Deployer` which will interpret the definition. Deployer implementation is registred using the `java.util.ServiceLoader` and selected through the `io.hyperfoil.deployer` system property. The default implementation is `ssh`.

## Common properties

| Property | Default        | Description                                                                  |
| -------- | -------------- | ---------------------------------------------------------------------------- |
| threads  | from benchmark | Number of threads used by the agent (overrides `threads` in benchmark root). |
| extras   |                | Custom options passed to the JVM (system properties, JVM options...)         |

## SSH deployer

The user account running Hyperfoil Controller must have a public-key authorization set up on agents' hosts using key `$HOME/.ssh/id_rsa`. It also has to be able to copy files into the `dir` directory using SCP - all the required JARs will be copied there and you will find the logs there as well.

`ssh` deployer accepts either the `[user@]host[:port]` inline syntax or these properties:

| Property | Default                                                                     | Description                                                                                                            |
| -------- | --------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| user     | Current username                                                            |                                                                                                                        |
| host     |                                                                             | This property is mandatory.                                                                                            |
| port     | 22                                                                          |                                                                                                                        |
| sshKey   | id_rsa                                                                      | Optionally define a different named key in the `$HOME/.ssh` directory                                                  |
| dir      | Directory set by system property `io.hyperfoil.rootdir` or `/tmp/hyperfoil` | Working directory for the agent. This directory can be shared by multiple agents running on the same physical machine. |
| cpu      | (all cpus)                                                                  | If set the CPUs where the agent can run is limited using `taskset -c &lt;cpu&gt;`. Example: `0-2,6`                    |

See an example of ssh deployment configuration:

```yaml
agents:
  agent1: testserver1:22
  agent2: testuser@testserver2
  agent3:
    host: testserver3
    port: 22
    dir: /some/other/path
```

## Kubernetes/Openshift deployer

To activate the kubernetes deployer you should set `-Dio.hyperfoil.deployer=k8s`; the [recommended installation](/docs/user-guide/installation/k8s/) does that automatically.

The agents are configured the same way as with SSH deployment, only the properties differ. Full reference is provided below.

Example:

```yaml
agents:
  my-agent:
    node: my-worker-node
```

| Property        | Default                                          | Description                                                                                                                                                                                                                                                                     |
| --------------- | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| node            |                                                  | Configures the labels for the `nodeSelector`. If the value does not contain equals sign (`=`) or comma (`,`) this sets the desired value of label `kubernetes.io/hostname`. You can also set multiple custom labels separated by commas, e.g. `foo=bar,kubernetes.io/os=linux`. |
| stop            | true                                             | By default the controller stops all agents immediatelly after the run terminates. In case of errors this is not too convenient as you might want to perform further analysis. To prevent automatic agent shutdown set this to false.                                            |
| log             |                                                  | Name of config map (e.g. `my-config-map`) or config map and its entry (e.g. `my-config-map/log4j2.xml`) that contains the Log4j2 configuration file. Default entry from the config map is `log4j2.xml`. Hyperfoil will mount this configmap as a volume to this agent.          |
| image           | quay.io/hyperfoil/hyperfoil:_controller-version_ | Different version of Hyperfoil in the agents                                                                                                                                                                                                                                    |
| imagePullPolicy | Always                                           | Image pull policy for agents                                                                                                                                                                                                                                                    |
| fetchLogs       | true                                             | Automatically watch agents' logs and store them in the run directory.                                                                                                                                                                                                           |

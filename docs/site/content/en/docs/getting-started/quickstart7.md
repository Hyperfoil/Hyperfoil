---
title: Clustered mode
description: Learn how to start the Hyperfoil server in clustered mode
categories: [Quickstart]
tags: [quickstart, server, clustered]
weight: 7
---

[Previously](/docs/getting-started/quickstart6) we've learned to start Hyperfoil in standalone server mode, and to do some runs through CLI. In this quickstart we'll see how to run your benchmark distributed to several agent nodes.

Hyperfoil operates as a cluster of [Vert.x](https://vertx.io/). When the benchmark is started, it deploys agents on other nodes according to the benchmark configuration - these are Vert.x nodes, too. Together controller and agents form a cluster and communicate over the event bus.

In this quickstart we'll use the SSH deployer; make sure your machine has SSH server running on port 22 and you can login using your pubkey `~/.ssh/id_rsa`. The SSH deployer copies the necessary JARs to `/tmp/hyperfoil/agentlib/` and starts the agent there. For instructions to run Hyperfoil in Kubernetes or Openshift please consult the [Installation docs](/docs/user-guide/installation/k8s/).

When we were running in the standalone or local mode we did not have to set any agents in the benchmark definition. That changes now as we need to inform the controller where the agents should be deployed. Let's see a benchmark - `two-agents.hf.yaml` that has those agents defined.

{{< readfile file="/static/benchmarks/two-agents.hf.yaml" code="true" lang="yaml" >}}

The load the benchmark generates is evenly split among the agents, so if you want to use another agent, you don't need to do any calculations - just add the agent and you're good to go.

Open three terminals; in the first start the controller using `bin/controller.sh`, in second one open the CLI with `bin/cli.sh` and in the third one start the example workload server:

```shell
podman run --rm -p 8080:8083 quay.io/hyperfoil/hyperfoil-examples
```

Connect, upload, start and check out the benchmark using CLI exactly the same way as we did in the previous quickstart:

```sh
[hyperfoil@localhost]$ connect
Connected!

[hyperfoil@localhost]$ upload .../two-agents.hf.yaml
Loaded benchmark two-agents, uploading...
... done.

[hyperfoil@localhost]$ run two-agents
Started run 004A

[hyperfoil@localhost]$ status
Run 004A, benchmark two-agents
Agents: agent-one[STARTING], agent-two[STARTING]
Started: 2019/04/17 17:08:19.703    Terminated: 2019/04/17 17:08:29.729
NAME  STATUS      STARTED       REMAINING  FINISHED      TOTAL DURATION
main  TERMINATED  17:08:19.708             17:08:29.729  10021 ms (exceeded by 21 ms)

[hyperfoil@localhost]$ stats
Total stats from run 004A
Phase   Sequence  Requests      Mean       p50       p90       p99     p99.9    p99.99    2xx    3xx    4xx    5xx Timeouts Errors
main:
	test:          106   3.12 ms   2.83 ms   3.23 ms  19.53 ms  25.30 ms  25.30 ms    106      0      0      0        0      0
```

You see that we did 106 requests which fits the assumption about running 10 user sessions per second over 10 seconds, while we have used 2 agents.

Vert.x clustering is using [Infinispan](http://infinispan.org/) and [JGroups](http://www.jgroups.org/); depending on your networking setup it might not work out-of-the-box. If you experience any trouble, check out the [FAQ](/docs/faq).

[Next quickstart](/docs/getting-started/quickstart8) will get back to the scenario definition; we'll show you how to extend Hyperfoil with custom steps and handlers.

---
title: Running the server
description: Learn how to start the Hyperfoil server in standalone mode
categories: [Quickstart]
tags: [quickstart, server, standalone]
weight: 6
---

Until now we have always started our benchmarks using an embedded _controller_ in the CLI, using the `start-local` command. This spawns a server in the CLI JVM. CLI communicates with it using standard REST API, though the server port is randomized and listens on localhost only. All the benchmarks and run results are also stored in `/tmp/hyperfoil/` - you can change the directory as an argument to the `start-local` command.
While the embedded controller might be convenient for a quick test or when developing the scenario it's not something that you'd use for a full-fledged benchmark.

When testing a reasonably performing system you need multiple nodes driving the load - we call them _agents_. These agents sync up, receive commands and report statistics to a master node, the _controller_. This node exposes a RESTful API to upload & start the benchmark, watch its progress and download results.

There are two other scripts in the `bin/` directory:

- `standalone.sh` starts both the controller and (one) agent in a single JVM. This is not too different from the controller embedded in CLI.
- `controller.sh` starts clustered [Vert.x](https://vertx.io/) and deploys the controller. Agents are started as needed in different nodes. You'll see this in the [next quickstart](/docs/getting-started/quickstart7).

Also note that it is possible to [run Hyperfoil in Openshift](/docs/user-guide/installation/k8s/).

Open two terminals; in one terminal start the standalone server and in second terminal start the CLI.

```sh
bin/standalone.sh
```

and

```sh
bin/cli.sh
```

Then, let's try to connect to the server (by default running on `http://localhost:8090`) and upload the `single-request` benchmark:

{{< readfile file="/static/benchmarks/single-request.hf.yaml" code="true" lang="yaml" >}}

From the second terminal, the one running the Hyperfoil CLI, issue the following commands:

```sh
[hyperfoil@localhost]$ connect
Connected! Server has these agents connected:
* localhost[REGISTERED]

[hyperfoil@localhost]$ upload .../single-request.hf.yaml
Loaded benchmark single-request, uploading...
... done.

[hyperfoil@localhost]$ run single-request
Started run 0001
```

When you switch to the first terminal (the one running the controller), you can see in the logs that the benchmark definition was stored on the server, the benchmark has been executed and its results have been stored to disk. Hyperfoil by default stores benchmarks in directory `/tmp/hyperfoil/benchmark` and data about runs in `/tmp/hyperfoil/run`; check it out:

```sh
column -t -s , /tmp/hyperfoil/run/0001/stats/total.csv
```
```sh
Phase    Name  Requests  Responses  Mean       Min        p50.0      p90.0      p99.0      p99.9      p99.99     Max        MeanSendTime  ConnFailure  Reset  Timeouts  2xx  3xx  4xx  5xx  Other  Invalid  BlockedCount  BlockedTime  MinSessions  MaxSessions
example  test  1         1          267911168  267386880  268435455  268435455  268435455  268435455  268435455  268435455  2655879       0            0      0         0    1    0    0    0      0        0             0
```

Reading CSV/JSON files directly is not too comfortable; you can check the details through CLI as well:

```sh
[hyperfoil@localhost]$ stats
Total stats from run 002D
Phase   Sequence  Requests      Mean       p50       p90       p99     p99.9    p99.99    2xx    3xx    4xx    5xx Timeouts Errors
example:
	test:            1 267.91 ms 268.44 ms 268.44 ms 268.44 ms 268.44 ms 268.44 ms      0      1      0      0        0      0
```

By the time you type the `stats` command into CLI the benchmark is already completed and the CLI shows stats for the whole run. Let's try running the {% include example_link.md src='eshop-scale.hf.yaml' %} we've seen in previous quickstart; this will give us some time to observe on-line statistics as the benchmark is progressing:

```sh
podman run --rm -p 8080:8083 quay.io/hyperfoil/hyperfoil-examples
```

```sh
[hyperfoil@localhost]$ upload .../eshop-scale.hf.yaml
Loaded benchmark eshop-scale, uploading...
... done.
[hyperfoil@localhost]$ run eshop-scale
Started run 0002
Run 0002, benchmark eshop-scale
...
```

Here the console would automatically jump into the `status` command, displaying the progress of the benchmark online. Press Ctrl+C to cancel that (it won't stop the benchmark run) and run the `stats` command:

```sh
[hyperfoil@localhost]$ stats
Recent stats from run 0002
Phase   Sequence  Requests      Mean       p50       p90       p99     p99.9    p99.99    2xx    3xx    4xx    5xx Timeouts Errors
buyingUserSteady/000:
        buy:             8   1.64 ms   1.91 ms   3.05 ms   3.05 ms   3.05 ms   3.05 ms      8      0      0      0        0      0
        browse:          8   2.13 ms   2.65 ms   3.00 ms   3.00 ms   3.00 ms   3.00 ms      8      0      0      0        0      0
browsingUserSteady/000:
        browse:          8   2.74 ms   2.69 ms   2.97 ms   2.97 ms   2.97 ms   2.97 ms      8      0      0      0        0      0
Press Ctr+C to stop watching...
```

You can go back to the run progress using the `status` command (hint: use `status --all` to display all phases, including those not started or already terminated):

```sh
[hyperfoil@localhost]$ status
Run 0002, benchmark eshop-scale
Agents: localhost[INITIALIZED]
Started: 2019/04/15 16:27:24.526
NAME                    STATUS   STARTED       REMAINING  FINISHED  TOTAL DURATION
browsingUserRampUp/006  RUNNING  16:28:54.565  2477 ms
buyingUserRampUp/006    RUNNING  16:28:54.565  2477 ms
Press Ctrl+C to stop watching...
```

Since we are showing this quickstart running the controller and CLI on the same machine it's easy to fetch results locally from `/tmp/hyperfoil/run/XXXX/...`. To save you SSHing into the controller host and finding the directories in a 'true remote' case there's the `export` command; This fetches statistics to your computer where you're running CLI. You can chose between default JSON format (e.g. `export 0002 -f json -d /path/to/dir`) and CSV format (`export 0002 -f csv -d /path/to/dir`) - the latter packs all CSV files into single ZIP file for your convenience.

When you find out that the benchmark is not going well, you can terminate it prematurely:

```sh
[hyperfoil@localhost]$ kill
Kill run 0002, benchmark eshop-scale(phases: 2 running, 0 finished, 40 terminated) [y/N]: y
Killed.
```

In the [next quickstart](/docs/getting-started/quickstart7) we will deal with starting clustered Hyperfoil.

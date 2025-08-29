---
title: First benchmark
description: Download, set up, and run your first Hyperfoil benchmark
categories: [Quickstart]
tags: [quickstart, benchmark]
weight: 1
---

#### 1. Download [latest release](https://github.com/Hyperfoil/Hyperfoil/releases/latest) and unpack it

```sh
wget {{< param url_latest_distribution >}} \
    && unzip {{< param zip_latest_distribution >}} \
    && cd <extracted dir>
```


#### 2. Start Hyperfoil in interactive mode (CLI)

```sh
bin/cli.sh
```

For our first benchmark we'll start an embedded server (controller) within the CLI:

```sh
[hyperfoil]$ start-local
Starting controller in default directory (/tmp/hyperfoil)
Controller started, listening on 127.0.0.1:41621
Connecting to the controller...
Connected!
```

#### 3. Upload the minimalistic benchmark and run it

As you can see below, the benchmark is really minimalistic as it is doing only single request to `http://hyperfoil.io`.

{{< readfile file="/static/benchmarks/single-request.hf.yaml" code="true" lang="yaml" >}}

Create the same benchmark in your local environment or [download it](/benchmarks/single-request.hf.yaml).
After that, upload it using the `upload` command as follows:

```sh
[hyperfoil@in-vm]$ upload .../single-request.hf.yaml
Loaded benchmark single-request, uploading...
... done.
[hyperfoil@in-vm]$ run single-request
Started run 0001
Run 0001, benchmark single-request
Agents: in-vm[STARTING]
Started: 2019/11/15 16:11:43.725    Terminated: 2019/11/15 16:11:43.899
<span class="hfcaption">NAME     STATUS      STARTED       REMAINING  COMPLETED     TOTAL DURATION               DESCRIPTION
example  TERMINATED  16:11:43.725             16:11:43.899  174 ms (exceeded by 174 ms)  1 users at once
```

#### 4. Check out performance results:

```sh
[hyperfoil@in-vm]$ stats
Total stats from run 000A
<span class="hfcaption">PHASE    METRIC  REQUESTS  MEAN       p50        p90        p99        p99.9      p99.99     2xx  3xx  4xx  5xx  CACHE  TIMEOUTS  ERRORS  BLOCKED
example  test           1  172.49 ms  173.02 ms  173.02 ms  173.02 ms  173.02 ms  173.02 ms    0    1    0    0      0         0       0       0 ns
```

Doing one request is not much of a benchmark and the statistics above are moot, but hey, this is a quickstart.

In the future you might find [editing with schema](/docs/howtos/editor) useful but at this point any editor with YAML syntax highlighting will do the job.

Ready? Let's continue with [something a bit more realistic...](/docs/getting-started/quickstart2)

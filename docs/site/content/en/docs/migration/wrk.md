---
title: Migrating from wrk/wrk2
description: How and why should I migrate from wrk/wrk2 to Hyperfoil?
categories: [Migration]
tags: [migration, wrk, wrk2]
weight: 1
---

Both Will Glozer's [wrk](https://github.com/wg/wrk) and Gil Tene's [wrk2](https://github.com/giltene/wrk2) are great tools but maybe you've realized that you need more functionalities, need to hit more endpoints in parallel or simply have to scale horizontally to more nodes. Hyperfoil offers an adapter that tries to mimic the behaviour of these load drivers. This guide will show how to use these and translate the test into a full-fledged Hyperfoil benchmark.

{{% alert title="Warning" color="warning" %}}
Please make sure that you are using Hyperfoil version >= `0.22`
{{% /alert %}}

Let's start with this command:

```sh
./wrk -c 10 -t 2 -d 10s -H 'accept: application/json' http://example.com
```

that will produce something like:
```sh
Running 10s test @ http://example.com
  2 threads and 10 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    97.64ms  489.01us 101.11ms   73.66%
    Req/Sec    50.73      4.81    80.00     91.92%
  1010 requests in 10.01s, 1.53MB read
Requests/sec:    100.85
Transfer/sec:    156.91KB
```

You can do exactly the same thing with Hyperfoil, either using `bin/wrk.sh`/`bin/wrk2.sh` or using container:

```sh
podman run --rm quay.io/hyperfoil/hyperfoil wrk -c 10 -t 2 -d 10s  -H 'accept: application/json' http://example.com/
```

that will generate something like:
```sh
Running 10s test @ http://example.com/
  2 threads and 10 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    98.61ms    9.58ms 402.65ms   98.92%
    Req/Sec   101.70      3.35   110.00     80.00
  1017 requests in 10.023s,   1.59MB read
Requests/sec: 101.47
Transfer/sec: 162.84kB
```

Not much of a difference as you can see. Note that if you want to test something in localhost you'd need to use host networking (`--net host`). You could also run it from CLI using the `wrk`/`wrk2` command. In that case you'd either connect to a controller using the `connect` command or start a controller inside the CLI with `start-local` command. When the (remote) controller is clustered you can use `-A`/`--agent` option (e.g. `-Amy-agent=host=my-server.my-company.com,port=22`) to drive the load from a different node.

Unlike original tools we support HTTP2 - this is disabled by default and you need to pass `--enable-http2` to allow it explicitly.

When you run the command from the CLI the benchmark stays in controller, so you can have a look on the statistics the same way as with any other Hyperfoil run (`stats`, `report`, `compare`, ...). You can also execute another run of the benchmark and observe the status as it progresses:

```sh
podman run -it --rm quay.io/hyperfoil/hyperfoil cli

[hyperfoil]$ start-local --quiet
[hyperfoil@in-vm]$ wrk -t 2 -c 10 -H 'accept: application/json' -d 10s http://example.com
Running 10s test @ http://example.com
  2 threads and 10 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    97.99ms  618.36?s 101.19ms   62.63%
    Req/Sec   102.50      2.94   110.00     90.00
  1025 requests in 10.006s,   1.61MB read
Requests/sec: 102.44
Transfer/sec: 164.41kB

[hyperfoil@in-vm]$ stats
Total stats from run 0000
Extensions (use -e to show): transfer
PHASE        METRIC   THROUGHPUT    REQUESTS  MEAN      p50       p90        p99        p99.9      p99.99     TIMEOUTS  ERRORS  BLOCKED  2xx   3xx  4xx  5xx  CACHE
calibration  request   96.58 req/s       608  99.39 ms  98.57 ms  100.14 ms  117.96 ms  406.85 ms  406.85 ms         0       0     0 ns   608    0    0    0      0
test         request  101.44 req/s      1025  97.99 ms  98.04 ms   99.09 ms  100.14 ms  101.19 ms  101.19 ms         0       0     0 ns  1015    0    0    0      0

[hyperfoil@in-vm]$ run wrk
Started run 0001
Run 0001, benchmark wrk
Agents: in-vm[READY]
Started: 2022/09/08 17:59:05.283
NAME  STATUS   STARTED       REMAINING      COMPLETED  TOTAL DURATION  DESCRIPTION
test  RUNNING  17:59:11.372  88 ms (88 ms)                             10 users always
```

Under the hood the command creates a benchmark that should reflect what the original `wrk`/`wrk2` implementation does. These do not have a YAML representation so you cannot view it with `info`/`edit` but you can use `inspect` to dig deep into the programmatic representation. The source YAML for the `wrk` invocation above would look like this:

```yaml
name: wrk
threads: 2 # option -t
http:
  host: http://example.com
  allowHttp2: false
  sharedConnections: 10 # option -c
ergonomics:
  repeatCookies: false
  userAgentFromSession: false
phases:
- calibration:
    always:
      users: 10 # option -c
      duration: 6s
      maxDuration: 70s # This is duration + default timeout 60s
      scenario: &scenario
      - request:
        - httpRequest:
            GET: /
            timeout: 60s # option --timeout
            headers:
            - accept: application/json # option -H
            handler:
              rawBytes: ... # Handler to record the data for 'Transfer/sec'
- test:
    always:
      users: 10 # option -c
      duration: 10s # option -d
      maxDuration: 10s # option -d
      startAfterStrict: calibration
      scenario: *scenario
```

As you can see there's a 'calibration' phase - `wrk` implementations might use it for different purposes but in our case it's convenient at least for a basic JVM warmup. The 'test' phase starts only when all requests from calibration phase complete. The scenario and number of users is identical, though.

With `wrk2` we use open model, you have to set request rate using the `-R` option - with `-R 20`; the phases would look like:

```yaml
phases:
- calibration:
    constantRate:
      usersPerSec: 20
      variance: false
      maxSessions: 300 # request rate x 15
      duration: 10s # option -d
      # ...
```

Hopefully this gives you some headstart and you can get familiar with Hyperfoil before diving into the details of benchmark syntax.

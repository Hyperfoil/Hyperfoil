---
date: 2020-12-09
title: Compensation for coordinated omission
linkTitle: Compensation
description: >
  TODO
author: TODO
---

Results for [closed-model](/docs/user-guide/benchmark/phases/) tests show maximum throughput your SUT can achieve; the response times are skewed though due to the coordinated omission. There are still cases where you'd like to run in this mode and check the size of the error you're accumulating.

Many benchmarks use closed-model with fixed concurrency and cycle-time, expecting the request to be fired every X milliseconds. When the requests fall behind in schedule due to some requests taking more than this period (cycle-time) the subsequent requests are fired as quickly as possible to catch up. The 'compensated' results estimate what would the latency be if you fired the requests on time. With open model the actual latencies could be the same (assuming that the previous request was delayed due waiting in a queue on server), better (the response was incidentally delayed on the network) or worse (the server queue would perform worse if hosting more requests).

Hyperfoil will tell you both values - the actually observed latencies AND the compensated estimate of latencies. You can use this mode only in `always` phases where you fix the cycle-time by providing a target rate of starting user sessions (contrary to the regular `always` behaviour starting user sessions as soon as possible). Note that the compensated time will be correct only if you don't execute any blocking steps before firing the request(s).

Example:
```yaml
name: test
http:
- host: http://example.com
  sharedConnections: 2
phases:
- test:
    always:
      users: 10
      duration: 20s
      scenario:
      - test:
        - httpRequest:
            GET: /
            compensation:
              targetRate: 10
```

The test above uses 10 concurrent users and tries to run at 10 requests/s. Below you can see CLI listing from the `stats` command, showing mean response time of 113 ms (real) vs. 172 ms (compensated). In this case the difference is not caused by a responses taking more than 1 second (the cycle-time for each user) but because sending the request is blocked by lack of available connections (we are using only 2): see that the `BLOCKED` column says that requests have been blocked for more than 6 seconds waiting for a free connection.

```shell
[hyperfoil@in-vm]$ stats
Total stats from run 0025
PHASE  METRIC            THROUGHPUT  REQUESTS  MEAN       p50        p90        p99        p99.9      p99.99     2xx  3xx  4xx  5xx  CACHE  TIMEOUTS  ERRORS  BLOCKED
test   compensated-test  9.97 req/s       208  171.78 ms  148.90 ms  216.01 ms  641.73 ms  968.88 ms  968.88 ms    0    0    0    0      0         0       0     0 ns
test   test              9.97 req/s       208  113.42 ms  103.28 ms  149.95 ms  312.48 ms  392.17 ms  392.17 ms  208    0    0    0      0         0       0  6.23 s
```

What happens underhood? You can see that using the `inspect` command in the CLI (some parts removed for brevity):

```yaml
scenario:
  initialSequences:
  - __delay-session-start:
    - delaySessionStart:
        randomize: true
        sequences:
        - test
        targetRate: 10.0
        targetRateIncrement: 0.0
  sequences:
  - test:
    - beforeSyncRequest: {}
    - httpRequest:
        handler:
        completionHandlers:
        - releaseSync: {}
        - compensatedResponseRecorder:
            metricSelector:
              prefixMetricSelector:
              delegate:
                providedMetricSelector:
                  name: test
              prefix: compensated-
            stepId: 21
        ...
```

The sequence `test` is removed from the `initialSequences` set and sequence `__delay-session-start` takes place instead. This contains single step `delaySessionStart` that waits until its due and then schedules the sequence `test`. The compensated time is recorded by another handler that reuses the metric selector from the request but adds `compensated-` prefix.
---
title: Phases - advanced
description: Delve into more advanced phase configuration
categories: [Quickstart]
tags: [quickstart, phases, link]
weight: 5
---

[Previous quickstart](/docs/getting-started/quickstart4) presented a benchmark with three phases that all started at the same moment (when the benchmark was started) and had the same duration - different phases represented different workflows (types of user). In this example we will adjust the benchmark to scale the load gradually up.

At this point it would be useful to mention the lifecycle of phases; phase is in one of these states:

- **not started**: As the name clearly says, the phase is not yet started.
- **running**: The agent started running the phase, i.e., performing the configured load.
- **finished**: When the `duration` elapses, no more new users are started. However, some might be still executing their scenarios.
- **terminated**: When all users complete their scenarios the phase becomes _terminated_. Users may be forcibly interrupted by setting `maxDuration` on the phase.
- **cancelled** If the benchmark cannot continue further, all remaining stages are cancelled.

Let's take a look into the example, where we'll slowly (over 5 seconds) increase load to 10+5 users/sec, run with this load for 10 seconds, again increase it by another 10+5 users/sec and so forth until we reach 100+50 users per second. As we define `maxIterations` for these phases the benchmark will actually contain phases `browsingUserRampUp/0`, `browsingUserRampUp/1`, `browsingUserRampUp/2` and so forth.

{{< readfile file="/static/benchmarks/eshop-scale.hf.yaml" code="true" lang="yaml" >}}

Don't forget to start the mock server as we've used in the previous quickstart.

```sh
podman run --rm -p 8080:8083 quay.io/hyperfoil/hyperfoil-examples
```

Synchronizing multiple workloads across iteration can become a bit cumbersome. That's why we can keep similar types of workflow together, and split the phase into _forks_. In fact forks will become different phases, but these will be linked together so that you can refer to all of them as to a single phase. Take a look at the benchmark rewritten to use forks:

{{< readfile file="/static/benchmarks/eshop-forks.hf.yaml" code="true" lang="yaml" >}}

This definition will create phases `rampUp/0/browsingUser`, `rampUp/0/buyingUser`, `rampUp/1/browsingUser` etc. - you'll see them in statistics.

You could orchestrate the phases as it suits you, using `startAfter`, `startAfterStrict` (this requires the referenced phase to me _terminated_ instead of _finished_ as with `startAfter`) or `startTime` with relative time since benchmark start.

This sums up basic principles, in [next quickstart](/docs/getting-started/quickstart6) you'll see how to start and use Hyperfoil in distributed mode.

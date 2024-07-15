---
title: FAQ
description: Frequently Asked Questions
categories: [FAQ]
tags: [faq]
weight: 3
---

## Why is Hyperfoil written in Java?

People are often concerned about JVM performance or predictability. While nowadays JVM is very good in the sense of throughput, dealing with jitter can be challenging. We are Java engineers, though, and we believe that these issues can be mitigated with a right design. That's why we try to be very careful on the execution hot-path.

We could achieve even better properties with C/C++, but the development effectivity would suffer. We could be succesful in Go, but we're not as intimate with its internals. Other languages and frameworks would pose its own challenges. So far, the choice of Java was not found to be a limiting factor.

## Why are you inventing your own YAML-based DSL instead of using Javascipt/Lua/...?

While some people might be more comfortable with describing their complex scenarios in a familiar language, running a script execution engine would have impact on performance and could put us out of control. We are not trying to invent a new language, written in YAML structure. Instead we propose a set of components common to many scenarios that could be recombined as it suits you. If you ever feel that the YAML is becoming cumbersome, try to move your complex benchmark logic to Java code and use it that way, instead.

## I just want to know what is the maximum throughput!

Maximum throughput is a single number which makes comparison very easy. Was my code change for better or worse? However finding the sweet spot is not as simple as throwing in few hundred concurrent threads running one request after another and taking the readings. With too high concurrency you can get worse results due to contention and longer queues, so you need to try different concurrency levels anyway.

There's nothing wrong with this type of test as long as you know what you're doing, and that the [response latencies might be far off reality](https://www.slideshare.net/slideshow/how-not-to-measure-latency-60111840/60111840). It's actually very good test when you look only for regressions - and Hyperfoil supports that, too.

## Hyperfoil is so hard to set up, I'll just use ...

Some tools can be run from the shell, with everything set just through options and arguments. That is quite handy for a quick test - and if the tool is sufficient for the job, use it. Or you can try runnning the same through Hyperfoil - e.g. for `wrk`/`wrk2` we offer a facade (CLI command `wrk` or `bin/wrk.sh`) that creates a benchmark with the same behaviour but you also get all the detailed results as from any other run - see [the migration guide](/docs/migration/wrk/). Once that your requirements outgrow what's possible in these simple tools, you can embrace the full power of benchmark composition.

## What does that 'Exceeded session limit' error mean?

With [open-model phase types](/docs/user-guide/benchmark/phase/) (constantRate, increasingRate, decreasingRate) the concurrency should not be limited. However as Hyperfoil tries not to allocate any memory during the benchmark we need to reserve space ahead for all sessions that could run concurrently - we call this the session limit. By default this limit is equal to number of users per second (assuming that the scenario won't take more than 1 second).

When you get the 'Exceeded session limit' error this means that some of the requests took a long time (or you have delays as part of the scenario) and Hyperfoil ran out of session pool. In that case you can change the limit using `maxSessions` property on the phase to the expected maximum concurrency. E.g. if you expect that the scenario will take 3 seconds and you're running at `usersPerSec: 100` you should set `maxSessions: 300` (or rather more to give it a buffer for unexpected jitter).

If increasing the limit doesn't help it usually means that the load at the tested system is too high and the responses are not arriving as fast as you fire the requests. In that case you should lower the load.

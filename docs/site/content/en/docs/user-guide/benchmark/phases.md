---
title: Phases
description: Defines a unit of workload simulation within a benchmark, representing a specific load pattern or behavior
categories: [Guide, Benchmark]
tags: [guides, benchmark, phases]
weight: 3
---

You might want to simulate several types of workloads at once: e.g. in an eshop users would come browsing or buying products, and operators would restock the virtual warehouse. Also, driving constant load may not be the best way to run the benchmark: often you want to slowly ramp the load up to let the system adjust (scale up, perform JIT, fill pools) and push the full load only after that. When trying to find system limits, you do the same repetitevely - ramp up the load, measure latencies and if the system meets SLAs (latencies below limits) continue ramping up the load until it breaks.

In Hyperfoil, this all is expressed through *phases*. Phases can run independently of each other;
these simulate certain load execute by a group of users. Within one phase all users execute the same `scenario`
(e.g. logging into the system, buying some goods and then logging off).

A phase can be in one of these states:
* _not running (scheduled)_: As the name clearly says, the phase is not yet getting executed.
* _running_: The agent started running the phase, i.e., performing the configured load.
* _finished_: Users won't start new scenarios but we'll let already-started users complete the scenario.
* _terminated_: All users are done, all stats are collected and no further requests will be made.
* _cancelled_: Same as terminated but this phase hasn't been run at all.

There are different types of phases based on the mode of starting new users:

| Type           | Description |
| -------------- | ----------- |
| constantRate   | The benchmark will start certain number of users according to a schedule regardless of previously started users completing the scenario. This is the open-model. |
| increasingRate | Similar to `constantRate` but ramps up the number of started users throughout the execution of the phase. |
| decreasingRate | The same as `increasingRate` but requires `initialUsersPerSec` > `targetUsersPerSec`. |
| atOnce         | All users are be started when the phase starts running and once the scenario is completed the users won't retry the scenario. |
| always         | There is fixed number of users and once the scenario is completed the users will start executing the scenario from beginning. This is called a closed-model and is similar to the way many benchmarks with fixed number of threads work. |
| noop           | This phase cannot have any scenario (or forks). It might be useful to add periods of inactivity into the benchmark. |

See the example of phases configuration:

```yaml
...
phases:
# Over one minute ramp the number of users started each second from 1 to 100
- rampUp:
    increasingRate:
      initialUsersPerSec: 1
      targetUsersPerSec: 100
      # We expect at most 200 users being active at one moment - see below
      maxSessions: 200
      duration: 1m
      scenario: ...
# After rampUp is finished, run for 5 minutes and start 100 new users each second
- steadyState:
    constantRate:
      usersPerSec: 100
      maxSessions: 200
      startAfter: rampUp
      duration: 5m
      # If some users get stuck, forcefully terminate them after 6 minutes from the phase start
      maxDuration: 6m
      scenario: ...
# 2 minutes after the benchmark has started spawn 5 users constantly doing something for 2 minutes
- outOfBand:
    always:
      users: 5
      startTime: 2m
      duration: 2m
      scenario: ...
- final:
    atOnce:
      users: 1
      # Do something at the end: make sure that both rampUp and steadyState are terminated
      startAfterStrict:
      - rampUp
      - steadyState
      scenario: ...
```

These properties are common for all types of phases:

| Property          | Description |
| ----------------- | ----------- |
| startTime         | Time relative to benchmark start when this phase should be scheduled. In other words, it's the earliest moment when it could be scheduled, other conditions (below) may delay that even further. |
| startAfter        | Phases that must be *finished* before this phase can start. You can use either single phase name, list of phases or a [reference to certain iteration](#iterations). |
| startAfterStrict  | Phases that must be *terminated* before this phase can start. Use the same syntax as for `startAfter`. |
| duration          | Intended duration for the phase (must be defined but for the `atOnce` type). After this time elapses no new sessions will be started; there might be some running sessions still executing operations, though. |
| maxDuration       | After this time elapses all sessions are forcefully terminated. |
| isWarmup          | This marker property is propagated to results JSON and allows the reporter to hide some phases by default. |
| maxUnfinishedSessions | Maximum number of session that are allowed to be open when the phase *finishes*. When there are more open sessions all the other sessions are cancelled and the benchmark is terminated. Unlimited by default. |
| maxIterations     | Maximum number of [iterations](#iterations) this phase will be scaled to. More about that below. |
| [scenario](/docs/user-guide/benchmark/scenario/) | The scenario this phase should execute. |
| [forks](#forks)             | See forks section below. |

Below are properties specific for different phase types:

* `atOnce`:
  * `users`: Number of users started at the start of the phase.
* `always`:
  * `users`: Number of users started at the start of the phase. When a user finishes it is immediatelly restarted (any pause must be part of the scenario).
* `constantRate`:
  * `usersPerSec`: Number of users started each second.
  * `variance`: Randomize delays between starting users following the [exponential distribution](https://en.wikipedia.org/wiki/Exponential_distribution). That way the starting users behave as the [Poisson point process](https://en.wikipedia.org/wiki/Poisson_point_process). If this is set to `false` users will be started with uniform delays. Default is `true`.
  * `maxSessions`: Number of preallocated sessions. This number is split between all agents/executors evenly.
* `increasingRate` / `decreasingRate`:
  * `initialUsersPerSec`: Rate of started users at the beginning of the phase.
  * `targetUsersPerSec`: Rate of started users at the end of the phase.
  * `variance`: Same as in `constantRate
  * `maxSessions`: Same as in `constantRate`.

Hyperfoil initializes all phases before the benchmark starts, pre-allocating memory for sessions.
In the open-model phases it's not possible to know how many users will be active at the same moment
(if the server experiences a 3-second hiccup and we have 100 new users per second this should be at least 300
as all the users will be blocked). However we need to provide the estimate for memory pre-allocation.
If the estimate gets exceeded the benchmark won't fail nor block new users from starting, but new sessions
will be allocated which might negatively impact results accuracy.

Properties `users`, `usersPerSec`, `initialUsersPerSec` and `targetUsersPerSec` can be either a scalar number or [scale with iterations](#iterations) using the `base` and `increment` components. You'll see an example below.

## Forks

As mentioned earlier, users in each phase execute the same scenario. Often it's convenient
to define the ramp-up and steady-state phases just once: the builders allow to declare such 'sub-phases' called forks.
For all purposes but the benchmark configuration these become regular phases of the same type, duration and dependencies (`startAfter`, `startAfterStrict`) as the 'parent' phase but slice the users according to their `weight`:

```yaml
...
phases:
- steadyState:
    constantRate:
      usersPerSec: 30
      duration: 5m
      forks:
        sellShares:
          # This phase will start 10 users per second
          weight: 1
          scenario: ...
        buyShares:
          # This phase will start 20 users per second
          weight: 2
          scenario: ...
```

These phases will be later identified as `steadyState/sellShares` and `steadyState/buyShares`. Other phases can still
reference `steadyState` (without suffix) as the dependency: there will be a no-op phase `steadyState` that starts (becomes *running*) as soon as both the forks *finish*, *finish* immediately and terminate once both the forks *terminate*.

## Iterations

In some types of tests it's useful to repeat given phase with increasing load - we call this concept *iterations*. In the example below you can see that `*usersPerSec` are not scalar values; in first iteration the actual value is set to the `base` value but in each subsequent iteration the value is increased by `increment`.

```yaml
...
phases:
- rampUp:
    increasingRate:
      # Create phases rampUp/000, rampUp/001 and rampUp/002
      maxIterations: 3
      # rampUp/000 will go from 1 to 100 users, rampUp will go from 101 to 200 users...
      initialUsersPerSec:
        base: 1
        increment: 100
      targetUsersPerSec:
        base: 100
        increment: 100
      # rampUp/001 will start after steadyState/000 finishes
      startAfter:
        phase: steadyState
        iteration: previous
      duration: 1m
      scenario: ...
- steadyState:
    constantRate:
      maxIterations: 3
      usersPerSec:
        base: 100
        increment: 100
      # steadyState/000 will start after rampUp/000 finishes
      startAfter:
        phase: rampUp
        iteration: same
      duration: 5m
```

Similar to forks, there will be a no-op phase `rampUp` that will start after all
`rampUp/xxx` phases finish and terminate after these terminate. Also there's an implicit dependency
between consecutive iterations: subsequent iteration won't start until previous iteration *terminates*.

The `startAfter` property in this example uses a relative reference to iteration in another phase. Each reference has these properties:

| Property  | Description |
| --------- | |
| phase     | Name of the referenced phase. |
| iteration | Relative number of the iteration; either `none` (default) which references the top-level phase, `same` meaning the iteration with same number, or `previous` with number one lower. |
| fork      | Reference to particular fork in the phase/iteration. |

Iterations can be combined with forks as well - the result name would be e.g. `steadyState/000/sellShares`.

Note that the `maxSessions` parameter is not scaling in iterations: all iterations execute the same
scenario, the execution does not overlap and therefore it is possible to share the pool of sessions.
Therefore you should provide an estimate for the iteration spawning the highest load.

## Staircase

Hyperfoil tries to make opinionated decisions, simplifying common types of benchmark setups. That's why it offers a simplified syntax for the scenario where you:

* ramp the load to a certain level
* execute steady state for a while
* ramp it up further
* execute another steady state
* repeat previous two steps over and over

This is called a *staircase* as the load increases in a shape of tilted stairs. Phases such benchmark should consist of are automatically created and linked together, using the same scenario/forks.

`staircase` as a top-level element in the benchmark is mutually exclusive to `scenario` and `phases` elements.

Here is a minimalistic example of such configuration:

{{< readfile file="/static/benchmarks/staircase.hf.yaml" code="true" lang="yaml" >}}

This element uses these properties:

| Property              | Description |
| --------------------- | ----------- |
| initialRampUpDuration | Duration of the very first phase. Default is no initial ramp-up. |
| initialUsersPerSec    | Rate of users starting at the end of the initial ramp-up. |
| steadyStateDuration   | Duration of each steady-state phase. |
| rampUpDuration        | Duration of each but first ramp-up. Default are no ramp-ups. |
| incrementUsersPerSec  | Increase in the rate of started users in each iteration. |
| maxIterations         | Maximum number of steady-state iterations. |
| [scenario](/docs/user-guide/benchmark/scenario/) | The scenario to be executed. |
| [forks](#forks)       | The forks with different scenarios. |

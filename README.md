# SailRocket

SailRocket is a distributed benchmarking framework designed to obtain
the most correct results (by avoiding the [coordinated omission problem](https://www.quora.com/In-Java-what-is-Coordinated-Omission))
and keeping very low allocation profile in the driver.

## Building

```
mvn clean package -DskipTests=true
```
will do the trick as usual. You'll find the distribution in `distribution/target/distribution`.
We'll refer to this location as `$DIST` later in this document.

## Architecture overview

SailRocket uses a master-slave design with single Controller that orchestrates the run
and one or more Agents that drive the load towards system-under-test (SUT).

Controller exposes a RESTful interface that can be accessed through the CLI or web-UI (TODO).

Controller and Agents communicate through Vert.x framework.

## Starting the server

Agent controller is started with
```
$DIST/bin/controller.sh
```
and agents are launched with
```
$DIST/bin/agent.sh
```
Any arguments passed to the scripts will be passed as-is to the `java` process.

By default each of these will launch a clustered Vert.x instance; you can also run
```
$DIST/bin/standalone.sh
```
which will launch non-clustered Vert.x instance with the controller and single agent.

These are the properties SailRocket recognizes:

| Property                      | Default           | Description                           |
  ------------------------------|-------------------|---------------------------------------|
| io.sailrocket.controller.host | localhost         | Host for Controller REST server       |
| io.sailrocket.controller.port |              8090 | Port for Controller REST server       |
| io.sailrocket.rootdir         | /tmp/sailrocket   | Root directory for stored files       |
| io.sailrocket.benchmarkdir    | *root*/benchmark  | Benchmark files (YAML and serialized) |
| io.sailrocket.rundir          | *root*/run        | Run result files (configs, stats...)  |

## Benchmark configuration

The benchmark can be created either through programmatic API (see `io.sailrocket.core.builders.BenchmarkBuilder`)
or through YAML configuration files. This section will focus on the YAML configuration. Here is an example of such configuration:

```yaml
name: complex benchmark
hosts:
  client1: user@driver1.my.lab.com
  client2: user@driver2.my.lab.com
  ...

simulation:
  http:
    baseUrl: http://localhost:8080
  phases: ...

```

The `hosts` section defines which agents should execute the benchmark.
**TODO** are hosts meant this way?

The `simulation` part describes how should the load be driven. Here we can see the `http` configuration
which sets base URL for all requests.
**TODO** common headers not implemented in parser

### Phases

Conceptually the simulation consists of several phases. Phases can run independently of each other;
these simulate certain load execute by a group of users. Within one phase all users execute the same `scenario`
(e.g. logging into the system, selling all their stock and then logging off).

A phase can be in one of these states:
* not running (scheduled)
* running
* finished: users won't start new scenarios but we'll let already-started users complete the scenario
* terminated: all users are done, all stats are collected and no further requests will be made

There are different types of phases based on the mode of starting new users:
* `atOnce`: All users are be started when the phase starts running
  and once the scenario is completed the users won't retry the scenario.
* `always`: There is fixed number of users and once the scenario is completed
  the users will start executing the scenario from beginning. This is called
  a closed-model and is similar to the way many benchmarks with fixed number
  of threads work.
* `constantPerSec`: The benchmark will start certain number of users according to a schedule
  regardless of previously started users completing the scenario. This is the open-model.
* `rampPerSec`: Similar to `constantPerSec` but ramps up or down the number of started users
  throughout the execution of the phase.

See the example of phases configuration:

```yaml
  ...
  phases:
  # Over one minute ramp the number of users started each second from 1 to 100
  - rampUp:
      rampPerSec:
        initialUsersPerSec: 1
        targetUsersPerSec: 100
        # We expect at most 200 users being active at one moment - see below
        maxSessionsEstimate: 200
        duration: 1m
        scenario: ...
  # After rampUp is finished, run for 5 minutes and start 100 new users each second
  - steadyState:
      constantPerSec:
        usersPerSec: 100
        maxSessionsEstimate: 200
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

The open-model phases specify rate of starting users using the `usersPerSec`, `initialUserPerSec` and `targetPerSec` properties.
With default settings the starting users behave as the [Poisson point process](https://en.wikipedia.org/wiki/Poisson_point_process),
effectively separating the start-times of successive users by random delays following
the [exponential distribution](https://en.wikipedia.org/wiki/Exponential_distribution).
If you prefer to start the users at fixed points in time (using uniform delays), set property `variance: false` in the phase.

SailRocket initializes all phases before the benchmark starts, pre-allocating memory for sessions.
In the open-model phases it's not possible to know how many users will be active at the same moment
(if the server experiences a 3-second hiccup and we have 100 new users per second this should be at least 300
as all the users will be blocked). However we need to provide the estimate for memory pre-allocation.
If the estimate gets exceeded the benchmark won't fail nor block new users from starting, but new sessions
will be allocated which might negatively impact results accuracy.

As mentioned earlier, users in each phase execute the same scenario. Often it's convenient
to define the ramp-up and steady-state phases just once: the builders allow to declare such 'sub-phases' called forks.
These become regular phases of the same type, duration and dependencies (`startAfter`, `startAfterStrict`) as the 'parent'
phase but slice the users according to their `weight`:

```yaml
  ...
  phases:
  - steadyState:
      constantPerSec:
        usersPerSec: 30
        duration: 5m
        forks:
        - sellShares:
            # This phase will start 10 users per second
            weight: 1
            scenario: ...
        - buyShares:
            # This phase will start 20 users per second
            weight: 2
            scenario: ...
```

These phases will be later identified as `steadyState/sellShares` and `steadyState/buyShares`. Other phases can still
reference `steadyState` (without suffix) as the dependency: there will be a no-op phase `steadyState` that starts (becomes *running*)
as soon as both the forks *finish*, *finish* immediately and terminate once both the forks *terminate*.

In some types of tests it's useful to repeat given phase with increasing load - we call this concept *iterations*.

```yaml
  ...
  phases:
  - rampUp:
      rampPerSec:
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
      constantPerSec:
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

Iterations can be combined with forks as well - the result name would be e.g. `steadyState/000/sellShares`.

Note that the `maxSessionsEstimate` parameter is not scaling in iterations: all iterations execute the same
scenario, the execution does not overlap and therefore it is possible to share the pool of sessions.
Therefore you should provide an estimate for the iteration spawning the highest load.

### Scenario

Scenario is a set of *sequences*. The sequence is the smallest unit of statistics reporting and consists of
several sequentially executed *steps*. Usually you should do only one request in each *sequence* as all
the requests will be reported together.

SailRocket is asynchronous framework and all steps must be non-blocking: therefore running e.g. `Thread.sleep(...)`
in a step is strictly prohibited. The state of the scenario execution is stored in a *session*.
A session will be always accessed by single thread at any moment but it's not guaranteed that it will be the same thread.

Contrary to steps in a sequence the sequences within a scenario do not need to be executed sequentially.
The scenario defines one or more `initialSequences` that are enabled from the beginning and other `sequences` that
must be enabled by any of the previously executed sequences. To be more precise it is not the *sequence*
that is enabled but a *sequence instance* as we can run a sequence multiple times in parallel (on different data).
The `initialSequences` enable one instance of each of the referenced sequence.

The session keeps a currently executed step for each of the enabled sequence instances. The step can be blocked
(e.g. waiting for a response to come). The session is looping through current steps in each of the enabled
sequence instances and if the step is not blocked, it is executed. There's no guaranteed order in which non-blocked
steps from multiple enabled sequence instances will be executed.

Here is an example of scenario:
```yaml
scenario:
  initialSequences:
  - login:
    - httpRequest:
        POST: /login
    # Wait until all requests sent get the response
    - awaitAllResponses
    # Enable instance of sequence 'wait5seconds'
    - next: wait5seconds
  sequences:
  - wait5seconds:
    - thinkTime:
        duration: 5s
    - next: logout
  - logout:
    - httpRequest:
        POST: /logout
    - awaitAllResponses
```

While this generic approach is useful for complex scenarios with branching logic,
simple sequential scenarios can use short-cut enabling sequences in given order:

```yaml
scenario:
  orderedSequences:
  - login:
    - httpRequest:
        POST: /login
    - awaitAllResponses
  - wait5seconds:
    - thinkTime:
        duration: 5s
  - logout:
    - httpRequest:
        POST: /logout
    - awaitAllResponses
```

You can use eiter well-known steps (those are defined as methods
on `i.s.core.builders.StepDiscriminator` class) or provide custom *service-loaded* steps.
These are provided by any implementation of `i.s.api.config.Step.BuilderFactory` that
is registered using the `java.util.ServiceLoader` mechanism. Each name provided by the factory
should be unique.

An exhaustive list of steps will be provided in the **TODO** reference.

### Anchors and aliases

Sequences such as logging into the systems will be likely used in different phases/scenarios
and it would be tedious to repeat these. That's where YAML anchors and aliases come into play:

```yaml
  ...
  phases:
  - rampUp:
      rampPerSec:
        scenario:
          orderedSequences:
          - login: &login
            - httpRequest:
                POST: /login
            - awaitAllResponses
            ...
  - steadyState:
      constantPerSec:
        ...
        scenario:
          orderedSequences:
          - login: *login
          ...
```

The steps from `steadyState/sellShares/login` will be copied verbatim to `steadyState/buyShares/login`.

The same concept can be applied on whole scenarios:

```yaml
  phases:
  - rampUp:
      rampPerSec:
        ...
        scenario: &doSomething
          orderedSequences:
          ...
  - steadyState:
      constantPerSec:
        ...
        scenario: *doSomething
```

And forks as well:

```yaml
  ...
  phases:
  - rampUp:
      rampPerSec:
        ...
        forks:
        - sellShares: &sellShares
            weight: 1
            scenario: ...
        - buyShares: &buyShares
            weight: 2
            scenario: ...
  - steadyState:
      constantPerSec:
        ...
        forks:
        - sellShares: *sellShares
        - buyShares: *buyShares
```

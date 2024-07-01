---
title: Scenario
description: Defines the behavior and sequence of actions that virtual users (VU) perform during a benchmark execution
categories: [Guide, Benchmark]
tags: [guides, benchmark, scenario]
weight: 4
---

# Scenario

Scenario is a set of *sequences*. The sequence is a block of sequentially executed *steps*. Contrary to steps in a sequence the sequences within a scenario do not need to be executed sequentially.

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
```

While this generic approach is useful for complex scenarios with branching logic, simple sequential scenarios can use `orderedSequences` short-cut enabling sequences in given order:

```yaml
scenario:
  orderedSequences:
  - login:
    - httpRequest:
        POST: /login
  - wait5seconds:
    - thinkTime:
        duration: 5s
  - logout:
    - httpRequest:
        POST: /logout
```

This syntax makes the first sequence (`login` in this case) an initial sequence, adds the subsequent sequences and as the last step of each but the last sequence appends a `next` step scheduling a new instance of the following sequence.

To make configuration even more concise you can omit the `orderedSequences` level and start defining the list of sequences under `scenario` right away:

```yaml
scenario:
- login:
  - httpRequest:
      POST: /login
- wait5seconds:
  - thinkTime:
      duration: 5s
- logout:
  - httpRequest:
      POST: /logout
```

An exhaustive list of steps can be found in the [steps reference](/docs/reference/steps/).

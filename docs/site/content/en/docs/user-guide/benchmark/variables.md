---
title: Variables
description: Data placeholders within sessions that hold values throughout the execution of a benchmark scenario
categories: [Guide, Benchmark]
tags: [guides, benchmark, variables]
weight: 5
---

All but the simplest scenarios will use session variables. Hyperfoil sports steps that generate values into these variables (`randomInt`, `randomItem`, ...), processors that write data from other sources to variables (`store`, `array`) and many places that read variables and use the values to perform some operations (`httpRequest.path` ) or alter control flow.

Hyperfoil uses different types of variables (slots in the session) for integer variables and generic objects (commonly strings). When a numeric value is received as a string (e.g. when parsing response headers) and you want to use it in a step that expects exclusively integral values you have to convert it explicitly, e.g. using the `stringToInt` action. Steps that read values to form a string can usually consume both types of variables, without any need for conversion.

Besides user-defined variables there are some read-only pseudo-variables that can be used in the scenario as if these were regular variables:

| Variable                   | Type    | Description |
| -------------------------- | ------- | ----------- |
| hyperfoil.agent.id         | integer | Zero-based index of the [agent node](/docs/user-guide/benchmark/agents/) |
| hyperfoil.agents           | integer | Number of [agent nodes](/docs/user-guide/benchmark/agents/) or 1 when running in in-VM mode (standalone or CLI) |
| hyperfoil.agent.thread.id  | integer | Zero-based index of current executor thread within this agent. |
| hyperfoil.agent.thread s   | integer | Number of executor threads running in this agent. |
| hyperfoil.global.thread.id | integer | Zero-based index of current executor thread across all agents (unique). |
| hyperfoil.global.threads   | integer | Total number of executor threads on all agents. |
| hyperfoil.phase.name       | object  | Full name of the currently executed phase (possibly including fork and iteration number). |
| hyperfoil.phase.id         | integer | Index of the currently executed phase. |
| hyperfoil.phase.iteration  | integer | Iteration number of the currently executed phase. |
| hyperfoil.run.id           | object  | Identifier of the current run, e.g. `0123`. |
| hyperfoil.session.id       | integer | Unique index of this virtual user (session). Note that in benchmarks with multiple phases the indices might not be zero-based. |

## String interpolation

Components that accept string values usually allow you to use a *pattern* - parts of the string can be replaced in runtime with the value from a session variable. A simple example of pattern would be `The quick brown ${wild-animal} jumps over the lazy ${domestic-animal}` - variables `wild-animal` and `domestic-animal` would get replaced with their respective values.

When you really want to use `${wild-animal}` in a value for such component you should escape it with one more dollar sign: `This $${variable} won't be replaced` will be rendered into `This ${variable} won't be replaced`.

There are a few transformations that you can perform with a variable value while interpolating the pattern:
* `${urlencode:my-variable}` will replace characters in the `my-variable` using [URLEncoder.encode](https://docs.oracle.com/javase/7/docs/api/java/net/URLEncoder.html#encode(java.lang.String,%20java.lang.String)) (using UTF-8 encoding).
* `${{ '{%05d' }}:my-number}` and other formatter strings ending with `d`, `o`, `x` or `X` will convert an integer variable using [Formatter](https://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html).
* `${replace/<regexp>/<replacement>/<flags>:my-variable}` perform Java regexp replacement on `my-variable` contents. Note that you can use any character after `replace`, not just `/` - this becomes the separator between regexp, replacement and flags. The only flags currently supported is `g` - replacing all occurences of that string (by default only first occurence is replaced).

## Sequence-scoped access

When an array or collection is stored in a session variable you can access the individual elements by appending `[.]` to the variable name, e.g. `my-variable[.]`. You can see that we don't use the actual index into the array: instead we use current sequence instance index. You can read more about running multiple sequences concurrently in the [Architecture/Scenario Execution](/docs/architecture/#scenario-execution).

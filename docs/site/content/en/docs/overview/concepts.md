---
title: Concepts
description: Hyperfoil key terms and concepts
categories: [Concepts]
tags: [concepts]
weight: 2
---

This document explains some core terms used throughout the documentation.

## Controller and agents

While it is possible to run benchmarks directly from CLI, in its nature Hyperfoil is a distributed tool with leader-follower architecture. **Controller** has the leader role; this is a [Vert.x](https://vertx.io)-based server with REST API. When a benchmark is started controller deploys agents (according to the benchmark definition), pushes the benchmark definition to these agents and orchestrates benchmark phases. **Agents** execute the benchmark, periodically sending statistics to the controller. This way the controller can combine and evaluate statistics from all agents on the fly. When the benchmark is completed all agents terminate.

All communication between the controller and agents happens over Vert.x eventbus - therefore it is independent on the deployment type.

## Phases

Conceptually the benchmark consists of several phases. Phases can run independently of each other;
these simulate certain load executed by a group of users (e.g. visitors vs. admins). Within one phase all users execute the same scenario (e.g. logging into the system, selling all their stock and then logging off).

Phases are also using for scaling the load during the benchmark; when looking for maximum throughput you schedule several iterations of given phase, gradually increasing the number of users that run the scenario.

A phase can be in one of these states:
* not running (scheduled)
* running
* finished: users won't start new scenarios but we'll let already-started users complete the scenario
* terminated: all users are done, all stats are collected and no further requests will be made

The state of phase on every agent is managed by Controller; this is also the finest grained unit of work it understands (controller has no information about state of each user).

## Sessions

The state of each user's scenario is saved in the session; sometimes we speak about (re)starting sessions instead of starting new users. Hyperfoil tries to keep allocations during benchmark as low as possible and therefore it pre-allocates all memory for the scenario execution ahead. This is why all resources the benchmark uses are capped - it needs to know the sizes of pools.

It is also necessary to know how many sessions we should preallocate - maximum concurrency of the system. If this threshold is exceeded Hyperfoil allocates further session as needed, but this is not the optimal mode of operation. It means that either you've underestimated the resources need or you've put a load on the system that it can't handle anymore, requests are not being completed and scenarios are not finished - which means that session objects cannot be recycled for reuse by next user.

## Scenario

Scenario consists of one or more **sequences** that are composed of **steps**. Steps are similar to statements in programming language and sequences are an equivalent of blocks of code.

While most of the time the scenario will consist of sequential operations as the user is not multi-tasking, the browser (or other system you're simulating) actually executes some operations in parallel - e.g. during page load it loads images concurrently. Therefore at any time the session contains one or more active **sequence instances**; when all sequence instances are done, the session has finished and can be recycled for a new user. Most of the time the scenario will start with only one active instance and as it progresses, it might create instances of other sequences (e.g after evaluating a condition it creates a sequence instance according to the branching logic).

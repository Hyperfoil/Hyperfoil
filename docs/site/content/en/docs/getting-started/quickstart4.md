---
title: Phases - basics
description: Deep dive into the basics of phases
categories: [Quickstart]
tags: [quickstart, phases]
weight: 4
---

[So far](/docs/getting-started/quickstart3) the benchmark contained only one type of load; certain number of users hitting the system, doing always the same (though data could be randomized). In practice you might want to simulate several types of workloads at once: in an eshop users would come browsing or buying products, and operators would restock the virtual warehouse.

Also, driving constant load may not be the best way to run the benchmark: often you want to slowly ramp the load up to let the system adjust (scale up, perform JIT, fill pools) and push the full load only after that. When trying to find system limits, you do the same repetitevely - ramp up the load, measure latencies and if the system meets SLAs (latencies below limits) continue ramping up the load until it breaks.

In Hyperfoil, this all is expressed through _phases_. We've already seen phases in the [first quickstart](/docs/getting-started/quickstart1) as we wanted to execute a non-default type of load - running the workload only once. Let's take a look on the "eshop" case first:

{{< readfile file="/static/benchmarks/eshop.hf.yaml" code="true" lang="yaml" >}}

Start the same server as you did in the previous quickstarts:

```shell
podman run --rm -p 8080:8083 quay.io/hyperfoil/hyperfoil-examples
```

In [next quickstart](/docs/getting-started/quickstart5) you'll learn how to repeat and link the phases.

---
title: Complex workflow
description: Start creating a more complex workflow
categories: [Quickstart]
tags: [quickstart]
weight: 3
---

The [previous example](/docs/getting-started/quickstart2) was the first 'real' benchmark, but it didn't do anything different from what you could run through `wrk`, `ab`, `siege` or similar tools.

Of course, the results were not suffering from the _coordinated omission problem_, but Hyperfoil can do more. Let's try a more complex scenario:

{{< readfile file="/static/benchmarks/choose-movie.hf.yaml" code="true" lang="yaml" >}}

Start the server and fire the scenario the usual way:

```shell
# start the server to interact with
podman run --rm -d -p 8080:8083 quay.io/hyperfoil/hyperfoil-examples

# start Hyperfoil CLI
bin/cli.sh
```

```sh
[hyperfoil]$ start-local
...
[hyperfoil@in-vm]$ upload .../choose-movie.hf.yaml
...
[hyperfoil@in-vm]$ run
...
```

Is this scenario too simplistic? Let's [define phases](/docs/getting-started/quickstart4)...

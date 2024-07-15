---
title: Hooks
description: Mechanisms that allow users to run specific scripts or commands automatically before and after executing a benchmark run
categories: [Guide, Benchmark]
tags: [guides, benchmark, hooks]
weight: 7
---


It might be useful to run certain scripts before and after the run, e.g. starting some infrastructure, preloading database, gathering CPU stats during the test and so on. That's why Hyperfoil introduces pre- and post-hooks to the run.

Some scripts are not specific to the test being run - these should be deployed on controller as files in `*root*/hooks/pre/` and `*root*/hooks/post` directories where *root* is controller's root directory, `/tmp/hyperfoil/` by default. Each of these directories should contain executable scripts or binaries that will be run in alphabetic order. We strongly suggest using the format `00-my-script.sh` to set the order using first two digits.

Kubernetes/Openshift deployments use the same strategy; the only difference is that the `pre` and `post` directories are mapped as volumes from a ConfigMap resource.

Other scripts may be specific to the benchmark executed and therefore you can define them directly in the YAML files. You can either use inline command that will be executed using `sh -c your-command --your-options` or create a Java class implementing `io.hyperfoil.core.hooks.RunHook` and register it to be [loaded as other Hyperfoil extensions](/docs/getting-started/quickstart8/).

```yaml
name: my-benchmark
pre:
  01-inline: curl http://example.com
  02-custom:
    my-hook:
      foo: bar
post:
  99-some-final-hook: ...
...
```

The lists of hooks from controller directories and benchmark are merged; if there's a conflict between two hooks from these two sources the final execution order is not defined (but both get executed).

In case of inline command execution the `stderr` output will stay on stderr, `stdout` will be caputered by Hyperfoil and stored in `*rundir*/*XXXX*/hooks.json`. As the post-hooks are executed after `info.json` and `all.json` get written the output cannot be included inside those files. This order of execution was chosen because it's likely that you will upload these files to a database - yes, using a post-hook.


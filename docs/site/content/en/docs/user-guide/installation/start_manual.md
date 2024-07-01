---
title: Manual startup
description: Explore manual startup options for the Hyperfoil controller.
categories: [Guide, Installation]
tags: [guides, installation, manual]
weight: 1
---

Hyperfoil controller is started with

```sh
bin/controller.sh
```

Any arguments passed to the scripts will be passed as-is to the `java` process.

By default `io.hyperfoil.deployer` is set to `ssh` which means that the controller will deploy agents over SSH, based on the [agents configurion](/docs/user-guide/benchmark/agent/#ssh-deployer). This requires that the user account running the controller must have public-key SSH authorization set up using key `$HOME/.ssh/id_rsa`. The user also has to be able to copy files to the directory set in agent definition (by default `/tmp/hyperfoil`) using SCP - Hyperfoil automatically synchronizes library files in this folder with the currently running instance and then executes the agent.

When you don't intend to run distributed benchmarks you can start the controller in _standalone_ mode:

```
bin/standalone.sh
```

This variant won't deploy any agents remotely and therefore it does not need any `agents: ` section in the [benchmark definition](/docs/user-guide/benchmark); instead it will use single agent started in the same JVM.

Below is the comprehensive list of all the properties Hyperfoil recognizes. All system properties can be replaced by environment variables, uppercasing the letters and replacing dots and dashes with underscores: e.g. `io.hyperfoil.controller.host` becomes `IO_HYPERFOIL_CONTROLLER_HOST`.

| Property                                  | Default            | Description                                                      |
| ----------------------------------------- | ------------------ | ---------------------------------------------------------------- |
| io.hyperfoil.controller.host              | 0.0.0.0            | Host for Controller REST server                                  |
| io.hyperfoil.controller.port              | 8090               | Port for Controller REST server                                  |
| io.hyperfoil.rootdir                      | /tmp/hyperfoil     | Root directory for stored files                                  |
| io.hyperfoil.benchmarkdir                 | _root_/benchmark   | Benchmark files (YAML and serialized)                            |
| io.hyperfoil.rundir                       | _root_/run         | Run result files (configs, stats...)                             |
| io.hyperfoil.deployer                     | ssh                | Implementation for agents deployment                             |
| io.hyperfoil.deployer.timeout             | 15000 ms           | Timeout for agents to start                                      |
| io.hyperfoil.agent.debug.port             |                    | If set, agent will be started with JVM debug port open           |
| io.hyperfoil.agent.debug.suspend          | n                  | Suspend parameter for the debug port                             |
| io.hyperfoil.controller.cluster.ip        | first non-loopback | Hostname/IP used for clustering with agents                      |
| io.hyperfoil.controller.cluster.port      | 7800               | Default JGroups clustering port                                  |
| io.hyperfoil.controller.external.uri      |                    | Externally advertised URI of REST server                         |
| io.hyperfoil.controller.keystore.path     |                    | File path to Java Keystore                                       |
| io.hyperfoil.controller.keystore.password |                    | Java Keystore password                                           |
| io.hyperfoil.controller.pem.keys          |                    | File path(s) to private TLS key(s) in PEM format                 |
| io.hyperfoil.controller.pem.certs         |                    | File path(s) to server TLS certificate(s) in PEM format          |
| io.hyperfoil.controller.password          |                    | Password used for Basic authentication                           |
| io.hyperfoil.controller.secured.via.proxy |                    | This must be set to `true` for Basic auth without TLS encryption |
| io.hyperfoil.trigger.url                  |                    | See below                                                        |

If `io.hyperfoi.trigger.url` is set the controller does not start benchmark run right away after hitting `/benchmark/my-benchmark/start` ; instead it responds with status 301 and header Location set to concatenation of this string and `BENCHMARK=my-benchmark&RUN_ID=xxxx`. CLI interprets that response as a request to hit CI instance on this URL, assuming that CI will trigger a new job that will eventually call `/benchmark/my-benchmark/start?runId=xxxx` with header `x-trigger-job`. This is useful if the the CI has to synchronize Hyperfoil to other benchmarks that don't use this controller instance.

## Security

Since Hyperfoil accepts and invoked any serialized Java objects you must not run it exposed to public to prevent a very simple remote code execution. Even if using HTTPS and password protection (see below) we recommend to limit access and privileges of the process to absolute minimum.

You can get confidential access to the server using TLS encryption, providing the certificate and keys either using Java Keystore mechanism (properties above starting with `io.hyperfoil.controller.keystore`) or via PEM files (properties starting with `io.hyperfoil.controller.pem`). These options are mutually exclusive. In the latter case it is possible to use multiple certificate/key files, separated by comma (,).

Authentication uses Basic authentication scheme accepting any string as username. The password is set using `io.hyperfoil.controller.password` or respective environment variable. If you're exposing the server using plaintext HTTP you must set `-Dio.hyperfoil.controller.secured.via.proxy=true` to confirm that this is a desired configuration (e.g. if the TLS is terminated at proxy and the connection from proxy does not require confidentiality).

---
title: Hyperfoil run script
description: How to quickly run Hyperfoil benchmarks
categories: [HowTo]
tags: [how-to, guides, run]
weight: 3
---

Starting from release `0.27`, Hyperfoil includes an easy-to-use script that simplifies running benchmarks, allowing  users to try tests faster when in-vm controller server is acceptable.

This script is particularly beneficial when you need to quickly test, validate or refine your benchmark
definitions, ensuring they run as expected without needing to manually orchestrate the controller and
agent processes. It also enables seamless integration into automation scripts or CI/CD pipelines, where
you can configure benchmarks to run as part of routine testing, with results saved for further analysis.
By simplifying the benchmark execution process, this script accelerates your workflow and allows for 
more streamlined performance testing with Hyperfoil.

### Key features
* In-vm controller: The script launches an in-VM Hyperfoil controller, so there's no need for users to
set up or manage an external controller.

* Benchmark upload & execution: Once the controller is running, the script automatically uploads the
benchmark you provide and triggers its execution. This minimizes manual setup, allowing users to 
focus on their test scenarios.

* No CLI interactions: Running the script does not require any CLI interaction, making this scipt
suitable for further automation.

* Automatic report generation: By adding the `--output <path-to-dir>` option, the script will generate
and save an HTML report of the test results in the specified directory, making it easy to review
performance data immediately after the benchmark completes.


### Usage


The syntax of this script is basically a superset of the `run` command, where the main argument is not the name of the benchmark but the benchmark file itself.

```bash
Usage: run [<options>] <benchmark>
Load and start a benchmark on Hyperfoil controller server, the argument can be the benchmark definition directly.

Options:
  -o, --output         Output destination path for the HTML report
  --print-stack-trace
  -d, --description    Run description
  -P, --param          Parameters in case the benchmark is a template. Can be set multiple times. Use `-PFOO=` to set the parameter to empty value and `-PFOO` to remove it and use default if available.
  -E, --empty-params   Template parameters that should be set to empty string.
  -r, --reset-params   Reset all parameters in context.

Argument:
                     Benchmark filename.
```

From the unzipped Hyperfoil distribution, you can simply run the script using the following format:

```
./distribution/bin/run.sh [-o OUTPUT_DIR] [-PPARAM1=.. -PPARAM2=..] BENCHMARK_FILE
```

For instance:
```bash
./distribution/bin/run.sh -o /tmp/reports /tmp/first-benchmark.yml
```

A valid output will be something like:

```bash
$ ./distribution/target/distribution/bin/run.sh -o /tmp/reports /tmp/first-benchmark.yml

Loaded benchmark first-benchmark, uploading...
... done.
Started run 0021
Monitoring run 0021, benchmark first-benchmark
Started:    2024/09/30 19:19:38.689
Terminated: 2024/09/30 19:19:49.532
Report written to /tmp/reports/0021.html
```

Alternatively you could also run the same directly using the Hyperfoil docker image:

```bash
docker run -it -v /tmp/benchmark/:/benchmarks:Z -v /tmp/reports:/tmp/reports:rw,Z -it --network=host quay.io/hyperfoil/hyperfoil run -o /tmp/reports /benchmarks/first-benchmark.yml
```

and the output will be the same:

```bash
$ docker run -it -v /tmp/benchmarks/:/benchmarks:Z -v /tmp/reports:/tmp/reports:rw,Z -it --network=host quay.io/hyperfoil/hyperfoil run -o /tmp/reports /benchmarks/first-benchmark.yml

Loaded benchmark first-benchmark, uploading...
... done.
Started run 0000
Monitoring run 0000, benchmark first-benchmark
Started:    2024/09/30 17:21:22.484
Terminated: 2024/09/30 17:21:32.490
Report written to /tmp/reports/0000.html
```
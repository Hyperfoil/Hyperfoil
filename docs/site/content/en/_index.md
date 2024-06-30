---
title: Hyperfoil
---

{{< blocks/cover title="Microservice-oriented distributed benchmark framework." height="auto" color="primary" >}}
<a class="btn btn-lg btn-dark me-3 mb-4" href="https://github.com/Hyperfoil/Hyperfoil">
  GitHub <i class="fab fa-github ms-2 "></i>
</a>
<a class="btn btn-lg btn-primary me-3 mb-4" href="/docs/">
  Learn More <i class="fas fa-arrow-alt-circle-right ms-2"></i>
</a>

<p class="lead mt-5"></p>
{{< blocks/link-down color="info" >}}

{{< /blocks/cover >}}

<!-- Hyperfoil main features -->
{{% blocks/section color="info" type="row" %}}

{{% blocks/feature icon="fa-network-wired" title="Distributed" %}}
Drive the load from </br>
many nodes.
{{% /blocks/feature %}}

{{% blocks/feature icon="fa-chart-line" title="Accurate" %}}
All operations are async to avoid the</br>
[coordinated-omission fallacy](https://www.slideshare.net/InfoQ/how-not-to-measure-latency-60111840).
{{% /blocks/feature %}}

{{% blocks/feature icon="fa-puzzle-piece" title="Versatile" %}}
You can express complex scenarios</br>
either in YAML or through pluggable</br>
steps.
{{% /blocks/feature %}}

{{% blocks/feature icon="fa-recycle" title="Low-allocation" %}}
Internally we try to allocate as little as</br>
possible on the critical code paths to</br>
not let garbage-collector disturb the</br>operations.
{{% /blocks/feature %}}

{{% /blocks/section %}}

<!-- News -->
{{% blocks/section color="dark" type="row" height="min" %}}
News
{.h1 .text-center}
<p class="lead mt-5"></p>

{{% blocks/feature icon="fas fa-newspaper" title="**Beginner's Guide to<br/>Hyperfoil: part 3**" url="/blog/news/2021-02-16-hf-beginner-guide-3/" %}}
  In this article we'll show how to run Hyperfoil<br/>
  inside an Openshift cluster, benchmarking workload<br/>
  within the same cluster.
{{% /blocks/feature %}}

{{% blocks/feature icon="fas fa-newspaper" title="**Beginner's Guide to<br/>Hyperfoil: part 2**" url="/blog/news/2021-02-09-hf-beginner-guide-2/" %}}
  In this post we will focus on processing of responses<br/>
  and user workflow through the site.
{{% /blocks/feature %}}

{{% blocks/feature icon="fas fa-newspaper" title="**Beginner's Guide to<br/>Hyperfoil: part 1**" url="/blog/news/2021-01-25-hf-beginner-guide-1/" %}}
  Meet Hyperfoil, a swiss-army knife of web benchmark<br/>
  driver. You'll learn how to write a simple benchmark<br/>
  and run it straight from the CLI.
{{% /blocks/feature %}}

<!-- TODO: reduce width -->
<!-- TODO: change font color -->
<!-- <a class="btn btn-lg me-3 mt-5" href="/docs/">
  Show Older Posts
</a> -->
{{% /blocks/section %}}

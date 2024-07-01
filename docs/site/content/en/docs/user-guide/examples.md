---
title: Examples
description: Collection of benchmark examples
categories: [Guide, Examples]
tags: [guides, examples]
weight: 4
---

If you haven't checked the [Getting started guide](/docs/getting-started/quickstart1/) we strongly recommend going there first.

Below you'll see commented examples of configuration; contrary to the Getting started guide these don't present scenarios but rather list the various configuration options by example.

## httpRequest

You will most likely use step `httpRequest` in each of your scenarios, and there's many ways to send a request.

{{< readfile file="/static/benchmarks/http-requests.hf.yaml" code="true" lang="yaml" >}}

Some scenarios need to access multiple HTTP endpoints; following example shows an example configuration for that:

{{< readfile file="/static/benchmarks/more-servers.hf.yaml" code="true" lang="yaml" >}}
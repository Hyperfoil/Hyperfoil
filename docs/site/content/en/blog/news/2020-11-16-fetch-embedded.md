---
date: 2020-11-16
title: Fetch embedded resources
linkTitle: Fetch embedded
description: >
  TODO
author: TODO
---

When browsing a website it's not only the main page the webserver needs to serve - usually there are static resources such as images, stylesheets, scripts and more. Hyperfoil can automatically download these.

Hyperfoil implements a non-allocating HTML parser, with a pre-baked `onEmbeddedResource` handler: This selects HTML tags with appropriate attributes that represent an URL:

* Images: `<img src="...">`
* Stylesheets: `<link href="...">`
* Scripts: `<script src="...">`
* Embedded media: `<embed src="...">`, `<object data="...">`
* Frames: `<frame src="...">`, `<iframe src="...">`

The other part of the solution is executing a `GET` request at given URL. Modern browsers don't download the resources one-by-one, nor do they fetch them all at once - usually these download around 6-8 resources in parallel. In Hyperfoil this is implemented by adding the URL to a queue. The queue delivers each URL to a new instance of a sequence (automatically generated) that fires the HTTP request, and limits the number of these sequences.

Since Hyperfoil pre-allocates all data structures ahead, you need to declare a limit on number of resources that can be fetched. If the queue cannot store more than `maxResources` URLs it simply discards the other URLs, emitting a warning to the log.

The parser signals the queue when there's no more data on the producing side, and when all of them are consumed the queue can fire some extra action. Note that completion of the original request does not wait until all the resources are fetched.

Below is an example of a configuration fetching embedded resources:

```yaml
- fetching:
  - httpRequest:
      GET: /foobar/index.html
      handler:
        body:
          parseHtml:
            onEmbeddedResource:
              fetchResource:
                maxResources: 16
                metric:
                # Drop the query part
                - ([^?]*)(\?.*)? -> $1
                onCompletion:
                  set: allFetched <- true
```

For details please [see the reference](/docs/reference/steps/processor_parseHtml).
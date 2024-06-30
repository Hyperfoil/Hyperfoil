---
date: 2020-11-16
title: Automatic follow of redirects
linkTitle: Follow redirects
description: >
  TODO
author: TODO
---

Hyperfoil is trying to imitate how users will hit your site with requests as realistically as possible. Therefore it supports one of the browser features: redirections.

There are two ways how webservers tell browsers where to fetch another page: HTTP-level redirects where the server returns status 3xx and the new URL in the `Location` header, and HTML with `<meta http-equiv="refresh" content="5; url=http://example.com" />`. The webpage can also use Javascript to switch to another page but Hyperfoil is not interpretting any Javascript.

You could implement the location-based redirect in YAML straight away:

```yaml
- redirectMe:
  - unset: location
  - httpRequest:
      GET: /redirectMe
      metric: first_request
      handler:
        header:
          filter:
            header:
              value: location
            processor:
              store: location
  - httpRequest:
      GET:
        fromVar: location
      metric: second_request
```

This would work in simple cases but it comes with several down-sides:

* You assume that the `/redirectMe` always responds with redirecting status. If the server can respond with other status you need to check its value and fence any handlers with sentinel based on status+location presence.
* Even with extra conditions the presented approach would not work if there is a series of redirections.
* If you want to apply the same data (headers, body...) or handlers to both requests, you'd need to copy-paste them.
* If you're issuing a `POST` instead of `GET` request some status values require that the second request is a `GET`.
* If the redirection URL is a relative one you'd need to compose the final URL yourselves.
* If there's a chance that the sequence is insantiated multiple times concurrently you'd need to transform `location` to a sequence-scoped variable.

Coding a general solution in YAML would be cumbersome, but you don't need to do that - all you need is:

```yaml
- redirectMe:
  - httpRequest:
      GET: /redirectMe
    handler:
      followRedirect: ALWAYS
```

Underhood this simple option adds/rewraps all the handlers (not presented in the example) as needed. If the response has a non-3xx status and there's no refresh `<meta />` tag everything is applied as usual. When the response suggests a redirect the request is executed in a new sequence. The request uses the same headers and body as the original request (evaluating the handlers second time, though) and all the original handlers are applied to the response. That includes handlers inserted through other 'magic' options, e.g. the `sync` option: if the original request was marked as synchronous the original sequence won't continue until the series of redirections ends with a non-redirecting request.

You might also want to react only to HTTP-level redirections (use `followRedirect: LOCATION_ONLY`) or the `<meta />` tag (use `followRedirect: HTML_ONLY`); the subsequent requests in the redirect chain will keep the same policy. The default value for the redirect is `NEVER` - both because of the principle of least surprise, to reduce memory footprint of the session and not complicate the debugging when redirects are not needed. If you prefer to keep the same policy for all requests in the benchmark you can change the default in the [ergonomics section](/docs/user-guide/benchmark/ergonomics/).

Note that while location-based redirect suppresses execution of all status-, headers- and body-handlers the HTML-redirect option (HTML redirect) runs all of them as usual even on the body part that does the redirect. Also, HTML-redirect supports the refresh timeout (load the page after X seconds) - this case warrants another generated sequence where the request is waiting to be executed.

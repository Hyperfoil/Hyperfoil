---
title: Troubleshooting
description: Common technical issues that you could hit during benchmark development
categories: [Guide, Troubleshooting]
tags: [guides, troubleshooting]
weight: 5
---

## It doesn't work. Can you help me?

The first step to identifying any issue is getting a verbose log - setting logging level to TRACE. How exactly you do that depends on the way you deploy Hyperfoil:

1. If you use CLI and the `start-local` command, just run it as `start-local -l TRACE` which sets the default logging level. You'll find the log in `/tmp/hyperfoil/hyperfoil.local.log` by default.

2. If you run Hyperfoil manually in [standalone mode](/docs/user-guide/installation/start_manual/) (non-clustered) the agent will run in the same JVM as the controller. You need to add `-Dlog4j.configurationFile=file:///path/to/log4j2-trace.xml` option when starting `standalone.sh`. If you start Hyperfoil through Ansible the same is set using `hyperfoil_log_config` variable.

3. If you run Hyperfoil in clustered mode, the failing code is probably in the agents. You need to pass the logging settings to agents using the deployer; with [SSH deployer](/docs/user-guide/benchmark/agents/#ssh-deployer) you need to add `-Dlog4j.configurationFile=file:///path/to/log4j2-trace.xml` to the `extras` property, in [Kubernetes/Openshift](/docs/user-guide/benchmark/agents/#kubernetesopenshift-deployer) there is the `log` option that lets you set the logging configuration through a config-map.

An example of Log4j2 configuration file with TRACE logging on is here:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
   <Appenders>
      <Console name="CONSOLE" target="SYSTEM_OUT">
         <PatternLayout pattern="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %m%n"/>
      </Console>
   </Appenders>
   <Loggers>
      <Root level="TRACE">
         <AppenderRef ref="CONSOLE"/>
      </Root>
   </Loggers>
</Configuration>
```

TRACE-level logging can be very verbose to a point where it will pose a bottleneck. It's recommended to isolate your problem at lower request rate if that's possible.

If you need to print variable values for debugging, check out [log step](/docs/reference/steps/step_log).

## My phase fails with SLA failure 'Progress was blocked waiting for a free connection. Hint: increase http.sharedConnections.'

By default Hyperfoil uses single connection to each HTTP(s) host; the default is set so low to force you thinking about connection limits early during test development. If you don't override this value as in:

```
http:
  host: http://hyperfoil.io
  sharedConnections: 1000
```

you get the error above, as the default SLA does not allow a session (virtual user) to be blocked due to not being able to acquire a connection from the connection pool immediatelly. If you can't increase number of connections (or use HTTP2 that allows multiple requests to multiplex within single connection), you can set

```
- httpRequest:
    sla:
    - blockedRatio: 1000 # any value big enough
```

on each request to drop the default SLA. The `blockedRatio` value is a threshold ratio between time spent waiting for a free connection and waiting for the response.

You could also wonder why the sessions are missing a connection when the scenario should guarantee there's always a free connection e.g. when using `always` phase type with same number of users and connections. However this may not hold when the connection is closed (either explicitly or after receiving a 5xx response) - while Hyperfoil starts replacing that connection immediatelly it takes a moment. If you expects connections to be closed add a few (10%) extra connections. Another reason could be poor balancing of connections and sessions to threads (should be gone in version 0.8).

## When I set 'Host' header for HTTP request I get warnings

Hyperfoil automatically inserts the 'Host' header to each request and when you try to override that for certain request it emits a warning:

```
Setting `host` header explicitly is not recommended. Use the HTTP host and adjust actual target using `addresses` property.
```

With this warning on we don't inject the header as it *might* be intended, e.g. when the target server does not parse headers in a case-sensitive way (as it should!) and you have to use certain case. However, if you want to run your requests to a different IP than the host resolves to (e.g. hit `127.0.0.1:8080` with `Host: example.com`) you should rather use

```
http:
  host: http://hyperfoil.io
  addresses:
  - 127.0.0.1:8080
```

## When I use a session variable I am seeing the error "Variable foo is not set!"

```
Errors:
in-vm: Variable foo is not set!
```

On occasion a scenario step has been seen to execute out of sequence. To ensure the variable is set beforehand use `initialSequences` with the step that populates the variable.


## My benchmark hangs indefinitely

If you experience your benchmark hanging indefinitely, 

This is usually reflected by having some phases marked as `FINISHED` and never terminated, i.ei, the `REMAINING` column
keeps decreasing over time.

{{% imgproc indefinite_hanging Fit "1600x200" %}}
{{% /imgproc %}}

This is most likely caused by a wrong assumption made as part of the benchmark configuration.

The indefinite hanging is usually caused by the `awaitVar` step that indefinitely waits for some variable to be set
even if that will never happen because of how the benchmark is configured.

**How do I know whether this is the problem I am facing?**

First of all just check whether the benchmark uses `awaitVar` and if so, double check whether there might be cases
where that variable the step is waiting for could be NOT set.

**Can you give me an example?**

Consider a simple use case where you are calling an endpoint and save the result into a variable:

```yaml
- httpRequest:
      GET: /login?email=${urlencode:email}&password=${urlencode:password}
      headers:
      accept: application/json
      handler:
      body:
         json:
            query: .token
            toVar: token
- awaitVar: token
```

Now consider that, for any reason, we have disabled the `autoRangeCheck` ergonomic, either globally

```yaml
ergonomics:
  autoRangeCheck: false
```

or on the `httpRequest`

```yaml
- httpRequest:
      GET: /login?email=${urlencode:email}&password=${urlencode:password}
      headers:
      accept: application/json
      handler:
      autoRangeCheck: false
      body:
         json:
            query: .token
            toVar: token
- awaitVar: token
```

This is a very simple example where we made the wrong assumption/configuration: we are waiting for a variable
`token` that is going to be set by the http request handler, but what happen if the request fails? Given that we disabled
the `autoRangeCheck`, that request won't be marked as invalid and the benchmark will proceed executing the next step
anyway (the `awaitVar`) that will wait for a state that will never happen.

Moreover, in this specific case we don't even need the `awaitVar` as by default the `httpRequest` steps are synchronouds by 
default - you change this behavior by setting `sync: false` at `httpRequest` level. See [httpRequest configuration](/docs/reference/steps/step_httprequest/)
for more details.

In conclusion, a couple of considerations:
* Use `awaitVar` only if dealing with *async* processes, by default this is not necessary as requests are *sync*
* Change the default ergonomics, e.g., `autoRangeCheck` or `stopOnInvalid`, with caution as they can heavily impact the overall behavior and
your expectations
---
date: 2021-01-25
title: "Beginner's Guide to Hyperfoil: part 2"
linkTitle: "Beginner's Guide 2"
description: >
  In this post we will focus on processing of responses and user workflow through the site.
author: TODO
---

> This article is intended to be published on other sites, too - therefore it contains introduction to concepts this blog’s readers are probably familiar with.

In the [previous part](/blog/news/2021-01-25-hf-beginner-guide-1/) we've deployed our demo application (Vehicle Market) and exercised some basic requests against that. In this post we will focus on processing responses and user workflow through the site.

## Processing responses

We will start with a benchmark that fetches single random page with an offering, without the HTML resources part for brevity:

```yaml
name: first-benchmark
http:
  host: http://localhost:8080
  sharedConnections: 10
duration: 10s
usersPerSec: 10
scenario:
- fetchDetails:
  - randomInt:
      min: 1
      max: 100
      toVar: offering
  - httpRequest:
      GET: /offering/${offering}
```

We have investigated what a browser would do, and found out that this page executes a request against `http://localhost:8082/offering/${offering}` to fetch a JSON document. Notice the different port `8082`: we will need to add another endpoint to the configuration and start selecting the endpoint in `httpRequest` steps:

```yaml
name: vehicle-market
http:
- host: http://localhost:8080
  sharedConnections: 10
- host: http://localhost:8082
  sharedConnections: 10
duration: 10s
usersPerSec: 10
scenario:
- fetchWebpage:
  - randomInt:
      min: 1
      max: 100
      toVar: offering
  - httpRequest:
      GET: /offering/${offering}
      authority: localhost:8080
- fetchJson:
  - httpRequest:
      GET: /offering/${offering}
      authority: localhost:8082
```

We have added the another sequence `fetchJson` with a second request. When the `scenario` contains the sequences as a list these are executed in-order; the second sequence is not started until the last step from the previous one completes. While you could keep both requests in one sequence, the sequence name is used as the default name for the metric. Therefore a metric with the same name would be reported twice. Moving the request to its own sequence solves the problem.

After receiving the JSON the script would modify the DOM and add images referenced in the JSON. Let's replicate that in our benchmark:

```yaml
name: vehicle-market
http:
- host: http://localhost:8080
  sharedConnections: 10
- host: http://localhost:8082
  sharedConnections: 10
duration: 10s
usersPerSec: 10
scenario:
  orderedSequences:
  - fetchWebpage:
    - randomInt:
        min: 1
        max: 2
        toVar: offering
    - httpRequest:
        GET: /offering/${offering}
        authority: localhost:8080
  - fetchJson:
    - httpRequest:
        GET: /offering/${offering}
        authority: localhost:8082
        handler:
          body:
            json:
              query: .gallery[].url
              toArray: gallery[10]
    - foreach:
        fromVar: gallery
        sequence:   fetchImage
        counterVar: numImages
    - awaitInt:
        var: numImages
        equalTo: 0
  sequences:
  - fetchImage[10]:
    - httpRequest:
        GET: ${ gallery[.] }
        authority: localhost:8080
        sync: false
        handler:
          onCompletion:
            addToInt: numImages--
```

The scenario does not host a simple list of sequences anymore; we have moved the original sequences under `orderedSequences` and added another section `sequences` with sequence `fetchImage`. The scenario starts with one instance of `fetchWebpage`, when this completes a single instance of `fetchJson` is created. There are no `fetchImage` instances at the beginning - `sequences` hosts only definitions but does not create any instances. For details see [documentation](/docs/user-guide/benchmark/scenario/)

In the request in `fetchJson` we have registered a handler for response body; This handler applies the `.gallery[].url` query and stores the URLs in an array stored in the session variable `gallery`. This array has 10 slots; as any other resource in Hyperfoil scenario this array is pre-allocated before the benchmark starts. Therefore we need to limit the size - if there are more images than slots the other URLs are simply discarded.

In the second step in `fetchJson` the `gallery` array is scanned by the [foreach](/docs/reference/steps/step_foreach) step. For each item in the array this steps creates a new instance of the `fetchImage` sequence. The `[10]` next to the sequence name means that there can be at most 10 instances of `fetchImage` running concurrently. The number of created sequences is then recorded into variable `numImages`.

> In fact the `foreach` step stops scanning the array when it finds an unset slot - gaps in the array are not supported. This is irrelevant for our scenario, though - the `toArray` fills the array from the start without gaps.

The last step in the `fetchJson` sequence is not necessary for our scenario (it would be complete after downloading all the images) but shows how to synchronize after all the images are retrieved. In the `awaitInt` step we are blocking the completion of the sequence until `numImages` drops to zero. The counter is decremented after the response with the image is fully received (`onCompletion` handler) using action [addToInt](/docs/reference/steps/action_addToInt).

We have not explained the notation `gallery[.]` in the path template fetching the image yet. The `[.]` is called sequence-scoped access: addressing an array with current sequence index. When `foreach` creates new instances of the same sequence each will get a distinct index (lowest available) that it can use to read/write its private data. Indices for different sequences are not coordinated, though.

## Login workflow

Now that you know how to process responses, let's have a look on another very common part of user workflow: authentication. We'd like to simulate user visiting the front page, clicking on the Login button and filing out the credentials, possibly multiple times to simulate fat fingers.

You need to get list of valid user credentials; Vehicle Market holds a copy of these (regular authentication flow uses hashed passwords) and you can get the list running:

```bash
curl localhost:8083/user-loader > /tmp/credentials.csv
```

Here is the benchmark:

```yaml
name: login
http:
# Frontend
- host: http://localhost:8080
  sharedConnections: 10
# User service
- host: http://localhost:8083
  sharedConnections: 10
duration: 10s
usersPerSec: 10
scenario:
  orderedSequences:
  - fetchIndex:
    - httpRequest:
        GET: /
        authority: localhost:8080
    - randomInt:
        min: 0
        max: 2
        toVar: failedAttempts
    - randomCsvRow:
        file: /tmp/credentials.csv
        removeQuotes: true
        columns:
          0: username
          1: password
  - wrongLogin:
    - breakSequence:
        intCondition:
          fromVar: failedAttempts
          equalTo: 0
        onBreak:
        - newSequence: successfulLogin
    - httpRequest:
        POST: /login
        authority: localhost:8083
        body:
          form:
          - name: username
            fromVar: username
          - name: password
            value: th1sIsMy$EcretPa55W%rD
        handler:
          autoRangeCheck: false
    - addToInt: failedAttempts--
    - nextSequence: wrongLogin
  sequences:
  - successfulLogin:
    - httpRequest:
        POST: /login
        authority: localhost:8083
        body:
          form:
          - name: username
            fromVar: username
          - name: password
            fromVar: password
```

There's nothing extraordinary in the first sequence, `fetchIndex` - we retrieve the landing page and decide if we should provide the correct credentials right away or have 1 or 2 failed attempts. We also select credentials using the `randomCsvRow` step. This step picks a random row from a CSV-formatted file, and stores the values into variables. In our case we pick the first column (columns are indexed starting from zero) into variable `username` and second into `password`.

After this we automatically jump to the `wrongLogin` sequence (even if we're not supposed to use wrong credentials). The first step there is the conditional `breakSequence` step: this step can terminate execution of its sequence prematurely (subsequent steps are not executed) and execute one or more actions. In `onBreak` we use the `newSequence` action that creates a new instance of sequence `successfulLogin`.

If the condition does not hold the execution of `wrongLogin` continues with the well known `httpRequest` step. This time we are firing a POST request, with a request body that will simulate a submitted HTML form. Hyperfoil will automatically add the `Content-Type: application/x-www-form-urlencoded` header and URL-encode the variables should there be any special characters. In this instance we're using a constant value for the password that should not match any actual user password.

By default Hyperfoil adds [handlers](/docs/reference/steps/step_httpRequest#handler) that will mark the response as invalid and stop session execution when the response status is not between 200 and 399. We're expecting a 401 response with invalid credentials and therefore we disable this default behaviour by setting `autoRangeCheck: false` (we don't need to disable the other handler, `stopOnInvalid`). Note that this behaviour can be also set globally in [ergonomics](/docs/user-guide/benchmark/ergonomics/).

After receiving the response (the request is synchronous) we decrement the number of failed attempts by 1 using the [addToInt](/docs/reference/steps/step_addToInt) step with shorthand syntax. We have used the `addToInt` action in the previous example: all actions can be used as steps, though steps (such as `httpRequest`) cannot be used as an action. This is not possible because a step can block sequence execution (waiting for an available connection, or until a variable is set...) but an action runs and completes without any delay - this is the main difference between those.

The last step is the `nextSequence` step (similar to the `newSequence` action) creating a new instance of the `wrongLogin` sequence. This step can be used anywhere in a sequence if it creates a different sequence or the sequence has sufficient concurrency limit (we had that `fetchImage[10]` in the previous example) - however had we added another step after it we would need two instances of `wrongLogin` running concurrently and the sequence is not marked as concurrent. When we place this as the last step there is a special case when the step only restarts current sequence, not requiring additional concurrent instance.

The `successfulLogin` sequence does not require much comment, it issues the same request as `wrongSequence`, only correctly picking the password from session variable. Let's have a look on the results:

```nohighlight
PHASE  METRIC           THROUGHPUT   REQUESTS  MEAN     p50      p90       p99       p99.9     p99.99    2xx  3xx  4xx  5xx  CACHE  TIMEOUTS  ERRORS  BLOCKED
main   fetchIndex       10.00 req/s       100  2.74 ms  3.01 ms   4.08 ms   4.85 ms   4.85 ms   4.85 ms  100    0    0    0      0         0       0     0 ns
main   successfulLogin  10.00 req/s       100  7.75 ms  8.32 ms  10.49 ms  11.53 ms  11.67 ms  11.67 ms  100    0    0    0      0         0       0     0 ns
main   wrongLogin        9.30 req/s        93  4.16 ms  4.98 ms   5.83 ms   6.98 ms   6.98 ms   6.98 ms    0    0   93    0      0         0       0     0 ns
```

We can now see 2xx responses for `successfulLogin` and 4xx responses for `wrongLogin` as we expect. Also the response times for a successful login are somewhat higher, maybe because the server stores a new token in the database.

Looking at browser network log we can see that the web-page captures this token and fetches user profile using that (it will also use this token in the `Authorization` header when talking to other services). Let's add this to our test, and one more thing: while Hyperfoil can send another login request almost immediately your users would need some time to type these. Therefore we are going to add some user think time:

```yaml
name: login
http:
# Frontend
- host: http://localhost:8080
  sharedConnections: 10
# User service
- host: http://localhost:8083
  sharedConnections: 10
duration: 10s
usersPerSec: 10
scenario:
  orderedSequences:
  - fetchIndex:
    - httpRequest:
        GET: /
        authority: localhost:8080
    - randomInt:
        min: 0
        max: 2
        toVar: failedAttempts
    - randomCsvRow:
        file: /tmp/credentials.csv
        removeQuotes: true
        columns:
          0: username
          1: password
    - thinkTime: 2s
  - wrongLogin:
    - breakSequence:
        intCondition:
          fromVar: failedAttempts
          equalTo: 0
        onBreak:
        - newSequence: successfulLogin
    - httpRequest:
        POST: /login
        authority: localhost:8083
        body:
          form:
          - name: username
            fromVar: username
          - name: password
            value: th1sIsMy$EcretPa55W%rD
        handler:
          autoRangeCheck: false
    - addToInt: failedAttempts--
    - thinkTime:
        duration: 2s
        random: NEGATIVE_EXPONENTIAL
        min: 500 ms
        max: 10s
    - nextSequence: wrongLogin
  sequences:
  - successfulLogin:
    - httpRequest:
        POST: /login
        authority: localhost:8083
        body:
          form:
          - name: username
            fromVar: username
          - name: password
            fromVar: password
        handler:
          body:
            store: token
    - nextSequence: fetchProfile
  - fetchProfile:
    - httpRequest:
        GET: /info?token=${urlencode:token}
        authority: localhost:8083
```

We have added constant 2-second pause as the last step of `fetchIndex`, and another pause into `wrongLogin` using [negative-exponential distribution](https://en.wikipedia.org/wiki/Exponential_distribution) with expected average of 2 seconds but ranging from 500 ms to 10 seconds (the actual average will be about 2044 ms due to these limits).

Then we have added a simple body handler to the successful login request, storing the value in session variable `token`, and a `nextSequence` step to the `successfulLogin` sequence that will start the `fetchProfile` sequence with single `httpRequest`. You can notice that we had to use a new notation in the pattern: `${urlencode:token}`. While pasting numbers into the request path is fine, a token might contain special symbols (such as +), and we need to URL-encode those. Contrary to the form used in the `successfulLogin` Hyperfoil cannot run the encoding automatically for you since it can't know if the session variable contents is already URL-encoded (e.g. if you fetched an existing URL into that).

Let's run this and see the output of `stats` command:

```sh
PHASE  METRIC           THROUGHPUT  REQUESTS  MEAN      p50       p90       p99       p99.9     p99.99    2xx  3xx  4xx  5xx  CACHE  TIMEOUTS  ERRORS  BLOCKED
main   fetchIndex       1.59 req/s        35   4.24 ms   3.54 ms   5.73 ms  13.83 ms  13.83 ms  13.83 ms   35    0    0    0      0         0       0     0 ns
main   fetchProfile     1.59 req/s        35   5.15 ms   4.69 ms   6.52 ms  22.68 ms  22.68 ms  22.68 ms   35    0    0    0      0         0       0     0 ns
main   successfulLogin  1.59 req/s        35  11.11 ms  10.75 ms  14.81 ms  36.96 ms  36.96 ms  36.96 ms   35    0    0    0      0         0       0     0 ns
main   wrongLogin       1.27 req/s        28   5.27 ms   5.44 ms   6.95 ms   7.44 ms   7.44 ms   7.44 ms    0    0   28    0      0         0       0     0 ns

main/fetchIndex: Exceeded session limit
main/fetchProfile: Exceeded session limit
main/successfulLogin: Exceeded session limit
main/wrongLogin: Exceeded session limit
```

In a colorful CLI you'd see all the lines in red and some errors listed below: "Exceeded session limit", and we did not run all the ~100 index page hits. What happened?

Hyperfoil has a fixed limit for concurrency - number of virtual users (sessions) executed in parallel. By default this limit is equal to user arrival rate (`usersPerSec`), so in this scenario it was 10 concurrent users. However as with all those think-times the session takes several seconds, we will require more than 10 concurrent sessions, even if the virtual users are idle in their think-time. Average session should take 4 seconds pausing plus some time for the requests, so we can expect little over 40 concurrent users. We'll add some margin and raise the limit to 60 sessions using the `maxSessions` property:

```yaml
name: login
http:
- host: http://localhost:8080
  sharedConnections: 10
- host: http://localhost:8083
  sharedConnections: 10
duration: 10s
usersPerSec: 10
maxSessions: 60
scenario: # ...
```

After running this we'll take a look on stats:

```nohighlight
PHASE  METRIC           THROUGHPUT  REQUESTS  MEAN     p50      p90       p99       p99.9     p99.99    2xx  3xx  4xx  5xx  CACHE  TIMEOUTS  ERRORS  BLOCKED
main   fetchIndex       4.46 req/s       106  2.58 ms  2.56 ms   3.88 ms   6.46 ms  10.03 ms  10.03 ms  106    0    0    0      0         0       0     0 ns
main   fetchProfile     4.46 req/s       106  3.44 ms  3.52 ms   4.46 ms   7.01 ms   9.90 ms   9.90 ms  106    0    0    0      0         0       0     0 ns
main   successfulLogin  4.46 req/s       106  8.47 ms  8.36 ms  11.40 ms  15.07 ms  28.70 ms  28.70 ms  106    0    0    0      0         0       0     0 ns
main   wrongLogin       4.92 req/s       117  4.12 ms  4.33 ms   4.98 ms  14.48 ms  28.70 ms  28.70 ms    0    0  117    0      0         0       0     0 ns
```

There are no errors and the request numbers are as expected. The throughput is somewhat off because the total duration of the phase was several seconds past - Hyperfoil starts the sessions within the configured 10 seconds, then the phase moves to a `FINISHED` state but it won't complete (state `TERMINATED`) until all sessions don't execute its last step and receive response for the last request.

We can also take a look on number of sessions running concurrently using the `sessions` command in the CLI:

```sh
[hyperfoil@in-vm]$ sessions
Run 003E has terminated.
PHASE  AGENT  MIN  MAX
main   in-vm    1   53
```

Our guess that we'll need 60 concurrent sessions was not too far off as at one moment we had 53 sessions running concurrently. You can also run this command when the test is being executed to see actual number of sessions rather than grand total for the whole phase.

This concludes our second blog post with a deep dive into complex scenarios. In the [next article](/blog/news/2021-02-16-hf-beginner-guide-3/) we'll go through setting Hyperfoil up in an OpenShift cluster.

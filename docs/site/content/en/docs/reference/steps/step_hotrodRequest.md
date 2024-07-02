---
title: "hotrodRequest"
description: "Issues a HotRod request and registers handlers for the response."
---
Issues a HotRod request and registers handlers for the response.

| Property | Type | Description |
| ------- | ------- | -------- |
| cacheName | String | Name of the cache used for the operation. This can be a <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>. |
| cacheName (alternative)| [Builder](#cachename) | <font color="#606060">&lt;no description&gt;</font> |
| get | String | Get specified entry in the remote cache. |
| key | String | Key used for the operation. This can be a <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>. |
| key (alternative)| [Builder](#key) | <font color="#606060">&lt;no description&gt;</font> |
| metric | String | Requests statistics will use this metric name. |
| metric (alternative)| [&lt;list of strings&gt;](#metric) | Allows categorizing request statistics into metrics based on the request path. |
| operation | enum | <br>Options:<ul><li><code>PUT</code>Adds or overrides each specified entry in the remote cache.</li><li><code>GET</code>Get specified entry in the remote cache.</li></ul> |
| put | String | Adds or overrides each specified entry in the remote cache. |
| value | String | Value for the operation. This can be a <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>. |
| value (alternative)| [Builder](#value) | <font color="#606060">&lt;no description&gt;</font> |

### cacheName

Generic builder for generating a string.


| Inline definition |
| -------- |
| A pattern for <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">string interpolation</a>. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | Object | Load the string from session variable. |
| pattern | String | Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing session variables. |
| value | String | String value used verbatim. |

### key

Generic builder for generating a string.


| Inline definition |
| -------- |
| A pattern for <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">string interpolation</a>. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | Object | Load the string from session variable. |
| pattern | String | Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing session variables. |
| value | String | String value used verbatim. |

### metric

Allows categorizing request statistics into metrics based on the request path.

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of strings&gt; | &lt;list of strings&gt; | Allows categorizing request statistics into metrics based on the request path. The expressions are evaluated in the order as provided in the list. Use one of: <ul> <li><code>regexp -&gt; replacement</code>, e.g. <code>([^?]*)(\?.*)? -&gt; $1</code> to drop the query part. <li><code>regexp</code> (don't do any replaces and use the full path), e.g. <code>.*.jpg</code> <li><code>-&gt; name</code> (metric applied if none of the previous expressions match). </ul> |

### value

Generic builder for generating a string.


| Inline definition |
| -------- |
| A pattern for <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">string interpolation</a>. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | Object | Load the string from session variable. |
| pattern | String | Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing session variables. |
| value | String | String value used verbatim. |


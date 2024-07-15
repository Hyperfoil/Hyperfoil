---
title: "collection"
description: "Collects results of processor invocation into a unbounded list. "
---
Collects results of processor invocation into a unbounded list. <br> WARNING: This processor should be used rarely as it allocates memory during the benchmark.

| Inline definition |
| -------- |
| Variable name to store the list. |


| Property | Type | Description |
| ------- | ------- | -------- |
| format | enum | Format into which should this processor convert the buffers before storing. Default is <code>STRING</code>.<br>Options:<ul><li><code>BYTEBUF</code>Store the buffer directly. Beware that this may cause memory leaks!</li><li><code>BYTES</code>Store data as byte array.</li><li><code>STRING</code>Interprets the bytes as UTF-8 string.</li></ul> |
| toVar | String | Variable name. |


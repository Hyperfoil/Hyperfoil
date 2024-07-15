---
title: "newSequence"
description: "Instantiates a sequence for each invocation."
---
Instantiates a sequence for each invocation.

| Inline definition |
| -------- |
| Sequence name. |


| Property | Type | Description |
| ------- | ------- | -------- |
| concurrencyPolicy | enum | <br>Options:<ul><li><code>FAIL</code></li><li><code>WARN</code></li></ul> |
| forceSameIndex | boolean | Forces that the sequence will have the same index as the currently executing sequence. This can be useful if the sequence is passing some data to the new sequence using sequence-scoped variables. Note that the new sequence must have same concurrency factor as the currently executing sequence. |
| sequence | String | Name of the instantiated sequence. |


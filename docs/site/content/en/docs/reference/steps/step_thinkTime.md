---
title: "thinkTime"
description: "Block current sequence for specified duration."
---
Block current sequence for specified duration.

| Inline definition |
| -------- |
| Duration of the delay with appropriate suffix (e.g. `ms` or `s`). |


| Property | Type | Description |
| ------- | ------- | -------- |
| duration | String | Duration of the delay with appropriate suffix (e.g. `ms` or `s`). |
| fromLast | &lt;none&gt; | Set previous delay point reference as the reference for next delay point; it will be computed as <code>(previous delay point or now) + duration</code>.<br>Note: property does not have any value |
| fromNow | &lt;none&gt; | Set this step invocation as the delay point reference; it will be computed as <code>now + duration</code>.<br>Note: property does not have any value |
| key | String | Key that is referenced later in `awaitDelay` step. If you're introducing the delay through `thinkTime` do not use this property. |
| max | String | Upper cap on the duration (if randomized). |
| min | String | Lower cap on the duration (if randomized). |
| random | enum | Randomize the duration.<br>Options:<ul><li><code>CONSTANT</code>Do not randomize; use constant duration.</li><li><code>LINEAR</code>Use linearly random duration between <code>min</code> and <code>max</code> (inclusively).</li><li><code>NEGATIVE_EXPONENTIAL</code>Use negative-exponential duration with expected value of <code>duration</code>, capped at <code>min</code> and <code>max</code> (inclusively).</li></ul> |
| type | enum | Alternative way to set delay reference point. See `fromNow` and `fromLast` property setters.<br>Options:<ul><li><code>FROM_LAST</code></li><li><code>FROM_NOW</code></li></ul> |


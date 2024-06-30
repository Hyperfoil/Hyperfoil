---
title: "addToSharedCounter"
description: "Adds value to a counter shared by all sessions in the same executor."
---
Adds value to a counter shared by all sessions in the same executor.

| Inline definition |
| -------- |
| Use on of: <code>counter++</code>, <code>counter--</code>, <code>counter += &lt;value&gt;</code>,
             <code>counter -= &lt;value&gt;</code> |


| Property | Type | Description |
| ------- | ------- | -------- |
| fromVar | String | Input variable name. |
| key | String | Identifier for the counter. |
| operator | enum | Operation to perform on the counter. Default is <code>ADD</code>.<br>Options:{::nomarkdown}<ul><li><code>ADD</code></li><li><code>SUBTRACT</code></li></ul>{:/} |
| value | int | Value (integer). |


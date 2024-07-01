---
title: "readAgentData"
description: "Reads data from agent-wide scope into session variable."
---
Reads data from agent-wide scope into session variable.<br> The data must be published in a phase that has terminated before this phase starts: usually this is achieved using the <code>startAfterStrict</code> property on the phase.

| Inline definition |
| -------- |
| Both the identifier and destination session variable. |


| Property | Type | Description |
| ------- | ------- | -------- |
| name | String | Unique identifier for the data. |
| toVar | String | Destination session variable name. |


---
title: "getSharedCounter"
description: "Retrieves value from a counter shared by all sessions in the same executor and stores that in a session variable. "
---
Retrieves value from a counter shared by all sessions in the same executor and stores that in a session variable. <br> If the value exceeds allowed integer range (-2^31 .. 2^31 - 1) it is capped.

| Inline definition |
| -------- |
| Both the key and variable name. |


| Property | Type | Description |
| ------- | ------- | -------- |
| key | String | Identifier for the counter. |
| toVar | String | Session variable for storing the value. |


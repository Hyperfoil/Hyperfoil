---
title: "store"
description: "Stores data in a session variable (overwriting on multiple occurences)."
---
Stores data in a session variable (overwriting on multiple occurences).

| Inline definition |
| -------- |
| Variable name. |


| Property | Type | Description |
| ------- | ------- | -------- |
| format | enum | Format into which should this processor convert the buffers before storing. Default is <code>STRING</code>.<br>Options:<ul><li><code>BYTEBUF</code>Store the buffer directly. Beware that this may cause memory leaks!</li><li><code>BYTES</code>Store data as byte array.</li><li><code>STRING</code>Interprets the bytes as UTF-8 string.</li></ul> |
| toVar | Object | Variable name. |


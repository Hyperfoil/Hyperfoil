---
title: "array"
description: "Stores data in an array stored as session variable."
---
Stores data in an array stored as session variable.

| Inline definition |
| -------- |
| Use format <code>toVar[maxSize]</code>. |


| Property | Type | Description |
| ------- | ------- | -------- |
| format | enum | Format into which should this processor convert the buffers before storing. Default is <code>STRING</code>.<br>Options:{::nomarkdown}<ul><li><code>BYTEBUF</code>: {:/}Store the buffer directly. Beware that this may cause memory leaks!{::nomarkdown}</li><li><code>BYTES</code>: {:/}Store data as byte array.{::nomarkdown}</li><li><code>STRING</code>: {:/}Interprets the bytes as UTF-8 string.{::nomarkdown}</li></ul>{:/} |
| maxSize | int | Maximum size of the array. |
| silent | boolean | Do not log warnings when the maximum size is exceeded. |
| toVar | String | Variable name. |


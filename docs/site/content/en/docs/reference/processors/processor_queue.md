---
title: "queue"
description: "Stores defragmented data in a queue. "
---
Stores defragmented data in a queue. <br> For each item in the queue a new sequence instance will be started (subject the concurrency constraints) with sequence index that allows it to read an object from an array using sequence-scoped access.

| Property | Type | Description |
| ------- | ------- | -------- |
| concurrency | int | Maximum number of started sequences that can be running at one moment. |
| format | enum | Conversion format from byte buffers. Default format is STRING.<br>Options:{::nomarkdown}<ul><li><code>BYTEBUF</code>: {:/}Store the buffer directly. Beware that this may cause memory leaks!{::nomarkdown}</li><li><code>BYTES</code>: {:/}Store data as byte array.{::nomarkdown}</li><li><code>STRING</code>: {:/}Interprets the bytes as UTF-8 string.{::nomarkdown}</li></ul>{:/} |
| maxSize | int | Maximum number of elements that can be stored in the queue. |
| onCompletion | [Action.Builder](index.html#actions) | Custom action that should be performed when the last consuming sequence reports that it has processed the last element from the queue. Note that the sequence is NOT automatically augmented to report completion. |
| sequence | String | Name of the started sequence. |
| var | String | Variable storing the array that it used as a output object from the queue. |


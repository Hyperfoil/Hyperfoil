---
title: "gzipInflator"
description: "Decompresses a GZIP data and pipes the output to delegated processors. "
---
Decompresses a GZIP data and pipes the output to delegated processors. <br> If the data contains multiple concatenated GZIP streams it will pipe multiple decompressed objects with <code>isLastPart</code> set to true at the end of each stream.

| Property | Type | Description |
| ------- | ------- | -------- |
| encodingVar | Object | Variable used to pass header value from header handlers. |
| processor | [Processor.Builder](index.html#processors) | Add one or more processors. |


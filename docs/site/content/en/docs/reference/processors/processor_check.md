---
title: "check"
description: "Configures a check on a response body. "
---
Configures a check on a response body. <br>
```
handler:
  body:
    check:
      equalTo: '{"result":1}'
```
 <br> Use <code>regex</code> or <code>json</code> instead of <code>equalTo</code> for the other matching strategies. Exactly one strategy must be configured.

| Property | Type | Description |
| ------- | ------- | -------- |
| equalTo | String | Requires the response body to have exactly these UTF-8 bytes, including its length. |
| json | String | Requires the response body to be structurally equal to this JSON value. Object property order is ignored, array order is preserved, and numbers compare mathematically: <code>1</code>, <code>1.0</code>, <code>1e0</code>, and negative zero are equal. A response body that repeats a property name within one object never matches. |
| regex | String | Requires the UTF-8 response body to fully match this regular expression. The dot matches line terminators as well. Malformed input is decoded using the Unicode replacement character. |

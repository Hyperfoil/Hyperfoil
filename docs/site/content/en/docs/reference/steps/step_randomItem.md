---
title: "randomItem"
description: "Stores random item from a list or array into session variable."
---
Stores random item from a list or array into session variable.

| Property | Type | Description |
| ------- | ------- | -------- |
| file | String | This file will be loaded into memory and the step will choose on line as the item. |
| fromVar | String | Variable containing an array or list. |
| list | [Builder](#list) | Potentially weighted list of items to choose from. |
| toVar | String | Variable where the chosen item should be stored. |

### <a id="list"></a>list

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;any&gt; | &lt;list of strings&gt; | Item as the key and weight (arbitrary floating-point number, defaults to 1.0) as the value. |
| &lt;list of strings&gt; | &lt;list of strings&gt; | Item as the key and weight (arbitrary floating-point number, defaults to 1.0) as the value. |


---
title: "pushSharedMap"
description: "Store values from session variables into a map shared across all sessions using the same executor into session variables. "
---
Store values from session variables into a map shared across all sessions using the same executor into session variables. 

 The executor can host multiple shared maps, each holding an entry with several variables. This step creates one entry in the map, copying values from session variables into the entry.

| Property | Type | Description |
| ------- | ------- | -------- |
| key | String | Key identifying the shared map. |
| vars | [&lt;list of strings&gt;](#vars) | List of variable names that should be stored in the entry. |

### <a id="vars"></a>vars

List of variable names that should be stored in the entry.

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of strings&gt; | &lt;unknown&gt; | <font color="#606060">&lt;no description&gt;</font> |


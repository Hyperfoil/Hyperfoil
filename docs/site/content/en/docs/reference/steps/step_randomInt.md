---
title: "randomInt"
description: "Stores random (linearly distributed) integer into session variable."
---
Stores random (linearly distributed) integer into session variable.

| Property | Type | Description |
| ------- | ------- | -------- |
| max | [Builder](#max) | Highest possible value (inclusive). Default is Integer.MAX_VALUE. |
| min | [Builder](#min) | Lowest possible value (inclusive). Default is 0. |
| toVar | String | Variable name to store the result. |

### <a id="max"></a>max


| Inline definition |
| -------- |
| Constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Initialize with a value from session variable. |
| value | int | Initialize with a constant value. |

### <a id="min"></a>min


| Inline definition |
| -------- |
| Constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Initialize with a value from session variable. |
| value | int | Initialize with a constant value. |


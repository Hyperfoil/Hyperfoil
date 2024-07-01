---
title: "set"
description: "Set variable in session to certain value."
---
Set variable in session to certain value.

| Inline definition |
| -------- |
| Use <code>var &lt;- value</code>. |


| Property | Type | Description |
| ------- | ------- | -------- |
| intArray | [Builder](#intarray) | Set variable to an (unset) integer array. |
| objectArray | [Builder](#objectarray) | Set variable to an (unset) object array. |
| value | String | String value. |
| var | String | Variable name. |

### <a id="intArray"></a>intArray

Creates integer arrays to be stored in the session.

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Contents of the new array. If the variable contains an array or a list, items will be copied to the elements with the same index up to the size of this array. If the variable contains a different value all elements will be initialized to this value. |
| size | int | Size of the array. |

### <a id="objectArray"></a>objectArray

Creates object arrays to be stored in the session.

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Contents of the new array. If the variable contains an array or a list, items will be copied to the elements with the same index up to the size of this array. If the variable contains a different value all elements will be initialized to this value. |
| size | int | Size of the array. |


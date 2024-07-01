---
title: "setInt"
description: "Set session variable to an integral value."
---
Set session variable to an integral value.

| Inline definition |
| -------- |
| Use <code>var &lt;- value</code>. |


| Property | Type | Description |
| ------- | ------- | -------- |
| fromVar | String | Input variable name. |
| intCondition | [Builder](#intcondition) | Set variable only if the current value satisfies certain condition. |
| max | [Builder](#max) | Set to value that is the maximum of this list of values. |
| min | [Builder](#min) | Set to value that is the minimum of this list of values. |
| onlyIfNotSet | boolean | Set variable to the value only if it is not already set. |
| value | int | Value (integer). |
| var | String | Variable name. |

### <a id="intCondition"></a>intCondition

| Property | Type | Description |
| ------- | ------- | ------- |
| equalTo | [Builder](#intconditionequalto) | Compared variable must be equal to this value. |
| greaterOrEqualTo | [Builder](#intconditiongreaterorequalto) | Compared variable must be greater or equal to this value. |
| greaterThan | [Builder](#intconditiongreaterthan) | Compared variable must be greater than this value. |
| lessOrEqualTo | [Builder](#intconditionlessorequalto) | Compared variable must be lower or equal to this value. |
| lessThan | [Builder](#intconditionlessthan) | Compared variable must be lower than this value. |
| notEqualTo | [Builder](#intconditionnotequalto) | Compared variable must not be equal to this value. |

### <a id="intCondition.equalTo"></a>intCondition.equalTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="intCondition.greaterOrEqualTo"></a>intCondition.greaterOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="intCondition.greaterThan"></a>intCondition.greaterThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="intCondition.lessOrEqualTo"></a>intCondition.lessOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="intCondition.lessThan"></a>intCondition.lessThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="intCondition.notEqualTo"></a>intCondition.notEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="max"></a>max

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of mappings&gt; | [&lt;list of builders&gt;](#maxlist-of-mappings) | <font color="#606060">&lt;no description&gt;</font> |

### <a id="max.&lt;list of mappings&gt;"></a>max.&lt;list of mappings&gt;


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="min"></a>min

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of mappings&gt; | [&lt;list of builders&gt;](#maxlist-of-mappings) | <font color="#606060">&lt;no description&gt;</font> |


---
title: "getSize"
description: "Calculates size of an array/collection held in variable into another variable"
---
Calculates size of an array/collection held in variable into another variable

| Property | Type | Description |
| ------- | ------- | -------- |
| boolFilter | [Builder](#boolfilter) | Count only items matching the condition. |
| fromVar | String | Variable holding the collection. |
| intFilter | [Builder](#intfilter) | Count only items matching the condition. |
| stringFilter | [Builder](#stringfilter) | Count only items matching the condition. |
| toVar | String | Variable storing the size. |
| undefinedValue | int | Value to use when <code>fromVar</code> is unset or it does not contain any array/collection. |

### boolFilter

| Property | Type | Description |
| ------- | ------- | ------- |
| value | boolean | Expected value. |

### intFilter

| Property | Type | Description |
| ------- | ------- | ------- |
| equalTo | [Builder](#intfilterequalto) | Compared variable must be equal to this value. |
| greaterOrEqualTo | [Builder](#intfiltergreaterorequalto) | Compared variable must be greater or equal to this value. |
| greaterThan | [Builder](#intfiltergreaterthan) | Compared variable must be greater than this value. |
| lessOrEqualTo | [Builder](#intfilterlessorequalto) | Compared variable must be lower or equal to this value. |
| lessThan | [Builder](#intfilterlessthan) | Compared variable must be lower than this value. |
| notEqualTo | [Builder](#intfilternotequalto) | Compared variable must not be equal to this value. |

### intFilter.equalTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intFilter.greaterOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intFilter.greaterThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intFilter.lessOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intFilter.lessThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intFilter.notEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringFilter

| Property | Type | Description |
| ------- | ------- | ------- |
| caseSensitive | boolean | True if the case must match, false if the check is case-insensitive. |
| endsWith | CharSequence | Suffix for the string. |
| equalTo | CharSequence | Literal value the string should match (the same as {@link #value}). |
| length | int | Check the length of the string. |
| length (alternative)| [Builder](#stringfilterlength) | Check the length of the string. |
| matchVar | String | Fetch the value from a variable. |
| negate | boolean | Invert the logic of this condition. Defaults to false. |
| notEqualTo | CharSequence | Value that the string must not match. |
| startsWith | CharSequence | Prefix for the string. |
| value | CharSequence | Literal value the string should match. |

### stringFilter.length

| Property | Type | Description |
| ------- | ------- | ------- |
| equalTo | [Builder](#stringfilterlengthequalto) | Compared variable must be equal to this value. |
| greaterOrEqualTo | [Builder](#stringfilterlengthgreaterorequalto) | Compared variable must be greater or equal to this value. |
| greaterThan | [Builder](#stringfilterlengthgreaterthan) | Compared variable must be greater than this value. |
| lessOrEqualTo | [Builder](#stringfilterlengthlessorequalto) | Compared variable must be lower or equal to this value. |
| lessThan | [Builder](#stringfilterlengthlessthan) | Compared variable must be lower than this value. |
| notEqualTo | [Builder](#stringfilterlengthnotequalto) | Compared variable must not be equal to this value. |

### stringFilter.length.equalTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringFilter.length.greaterOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringFilter.length.greaterThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringFilter.length.lessOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringFilter.length.lessThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringFilter.length.notEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |


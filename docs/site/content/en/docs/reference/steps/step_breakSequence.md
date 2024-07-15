---
title: "breakSequence"
description: "Prematurely stops execution of this sequence if the condition is satisfied."
---
Prematurely stops execution of this sequence if the condition is satisfied.

| Property | Type | Description |
| ------- | ------- | -------- |
| allConditions | [Builder](#allconditions) | Condition combining multiple other conditions with 'AND' logic. |
| boolCondition | [Builder](#boolcondition) | Condition comparing boolean variables. |
| dependency | String | This step is blocked if this variable does not have set value (none by default). |
| intCondition | [Builder](#intcondition) | Condition comparing integer variables. |
| onBreak | [Action.Builder](index.html#actions) | Action performed when the condition is true and the sequence is to be ended. |
| stringCondition | [Builder](#stringcondition) | Condition comparing string variables. |

### allConditions

Test more conditions and combine the results using AND logic.

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of mappings&gt; | [&lt;list of builders&gt;](#allconditionsltlist-of-mappingsgt) | List of conditions. |

### allConditions.&lt;list of mappings&gt;

Selector for condition type.

| Property | Type | Description |
| ------- | ------- | ------- |
| allConditions | [Builder](#allconditionsltlist-of-mappingsgtallconditions) | Condition combining multiple other conditions with 'AND' logic. |
| boolCondition | [Builder](#allconditionsltlist-of-mappingsgtboolcondition) | Condition comparing boolean variables. |
| intCondition | [Builder](#allconditionsltlist-of-mappingsgtintcondition) | Condition comparing integer variables. |
| stringCondition | [Builder](#allconditionsltlist-of-mappingsgtstringcondition) | Condition comparing string variables. |

### allConditions.&lt;list of mappings&gt;.allConditions

Test more conditions and combine the results using AND logic.

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of mappings&gt; | [&lt;list of builders&gt;](#allconditionsltlist-of-mappingsgt) | List of conditions. |

### allConditions.&lt;list of mappings&gt;.boolCondition

Tests session variable containing boolean value.

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Variable name. |
| value | boolean | Expected value. |

### allConditions.&lt;list of mappings&gt;.intCondition

Condition comparing integer in session variable.


| Inline definition |
| -------- |
| Parses condition in the form &lt;variable&gt; &lt;operator&gt; &lt;value&gt;
             where operator is one of: <code>==</code>, <code>!=</code>,
             <code>&lt;&gt;</code> (the same as <code>!=</code>),
             <code>&gt;=</code>, <code>&gt;</code>, <code>&lt;=</code>, <code>&lt;</code>. |

| Property | Type | Description |
| ------- | ------- | ------- |
| equalTo | [Builder](#intconditionequalto) | Compared variable must be equal to this value. |
| fromVar | Object | Variable name. |
| greaterOrEqualTo | [Builder](#intconditiongreaterorequalto) | Compared variable must be greater or equal to this value. |
| greaterThan | [Builder](#intconditiongreaterthan) | Compared variable must be greater than this value. |
| isSet | boolean | Check if the value is set or unset. By default the variable must be set. |
| lessOrEqualTo | [Builder](#intconditionlessorequalto) | Compared variable must be lower or equal to this value. |
| lessThan | [Builder](#intconditionlessthan) | Compared variable must be lower than this value. |
| notEqualTo | [Builder](#intconditionnotequalto) | Compared variable must not be equal to this value. |

### allConditions.&lt;list of mappings&gt;.stringCondition

Condition comparing string in session variable.

| Property | Type | Description |
| ------- | ------- | ------- |
| caseSensitive | boolean | True if the case must match, false if the check is case-insensitive. |
| endsWith | CharSequence | Suffix for the string. |
| equalTo | CharSequence | Literal value the string should match (the same as {@link #value}). |
| fromVar | Object | Variable name. |
| isSet | boolean | Check if the value is set or unset. By default the variable must be set. |
| length | int | Check the length of the string. |
| length (alternative)| [Builder](#stringconditionlength) | Check the length of the string. |
| matchVar | String | Fetch the value from a variable. |
| negate | boolean | Invert the logic of this condition. Defaults to false. |
| notEqualTo | CharSequence | Value that the string must not match. |
| startsWith | CharSequence | Prefix for the string. |
| value | CharSequence | Literal value the string should match. |

### boolCondition

Tests session variable containing boolean value.

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Variable name. |
| value | boolean | Expected value. |

### intCondition

Condition comparing integer in session variable.


| Inline definition |
| -------- |
| Parses condition in the form &lt;variable&gt; &lt;operator&gt; &lt;value&gt;
             where operator is one of: <code>==</code>, <code>!=</code>,
             <code>&lt;&gt;</code> (the same as <code>!=</code>),
             <code>&gt;=</code>, <code>&gt;</code>, <code>&lt;=</code>, <code>&lt;</code>. |

| Property | Type | Description |
| ------- | ------- | ------- |
| equalTo | [Builder](#intconditionequalto) | Compared variable must be equal to this value. |
| fromVar | Object | Variable name. |
| greaterOrEqualTo | [Builder](#intconditiongreaterorequalto) | Compared variable must be greater or equal to this value. |
| greaterThan | [Builder](#intconditiongreaterthan) | Compared variable must be greater than this value. |
| isSet | boolean | Check if the value is set or unset. By default the variable must be set. |
| lessOrEqualTo | [Builder](#intconditionlessorequalto) | Compared variable must be lower or equal to this value. |
| lessThan | [Builder](#intconditionlessthan) | Compared variable must be lower than this value. |
| notEqualTo | [Builder](#intconditionnotequalto) | Compared variable must not be equal to this value. |

### intCondition.equalTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intCondition.greaterOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intCondition.greaterThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intCondition.lessOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intCondition.lessThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### intCondition.notEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringCondition

Condition comparing string in session variable.

| Property | Type | Description |
| ------- | ------- | ------- |
| caseSensitive | boolean | True if the case must match, false if the check is case-insensitive. |
| endsWith | CharSequence | Suffix for the string. |
| equalTo | CharSequence | Literal value the string should match (the same as {@link #value}). |
| fromVar | Object | Variable name. |
| isSet | boolean | Check if the value is set or unset. By default the variable must be set. |
| length | int | Check the length of the string. |
| length (alternative)| [Builder](#stringconditionlength) | Check the length of the string. |
| matchVar | String | Fetch the value from a variable. |
| negate | boolean | Invert the logic of this condition. Defaults to false. |
| notEqualTo | CharSequence | Value that the string must not match. |
| startsWith | CharSequence | Prefix for the string. |
| value | CharSequence | Literal value the string should match. |

### stringCondition.length

| Property | Type | Description |
| ------- | ------- | ------- |
| equalTo | [Builder](#stringconditionlengthequalto) | Compared variable must be equal to this value. |
| greaterOrEqualTo | [Builder](#stringconditionlengthgreaterorequalto) | Compared variable must be greater or equal to this value. |
| greaterThan | [Builder](#stringconditionlengthgreaterthan) | Compared variable must be greater than this value. |
| lessOrEqualTo | [Builder](#stringconditionlengthlessorequalto) | Compared variable must be lower or equal to this value. |
| lessThan | [Builder](#stringconditionlengthlessthan) | Compared variable must be lower than this value. |
| notEqualTo | [Builder](#stringconditionlengthnotequalto) | Compared variable must not be equal to this value. |

### stringCondition.length.equalTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringCondition.length.greaterOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringCondition.length.greaterThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringCondition.length.lessOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringCondition.length.lessThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### stringCondition.length.notEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |


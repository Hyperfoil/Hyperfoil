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

### <a id="allConditions"></a>allConditions

Test more conditions and combine the results using AND logic.

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of mappings&gt; | [&lt;list of builders&gt;](#allconditionslist-of-mappings) | List of conditions. |

### <a id="allConditions.&lt;list of mappings&gt;"></a>allConditions.&lt;list of mappings&gt;

Selector for condition type.

| Property | Type | Description |
| ------- | ------- | ------- |
| allConditions | [Builder](#allconditionslist-of-mappingsallconditions) | Condition combining multiple other conditions with 'AND' logic. |
| boolCondition | [Builder](#allconditionslist-of-mappingsboolcondition) | Condition comparing boolean variables. |
| intCondition | [Builder](#allconditionslist-of-mappingsintcondition) | Condition comparing integer variables. |
| stringCondition | [Builder](#allconditionslist-of-mappingsstringcondition) | Condition comparing string variables. |

### <a id="allConditions.&lt;list of mappings&gt;.allConditions"></a>allConditions.&lt;list of mappings&gt;.allConditions

Test more conditions and combine the results using AND logic.

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of mappings&gt; | [&lt;list of builders&gt;](#allconditionslist-of-mappings) | List of conditions. |

### <a id="allConditions.&lt;list of mappings&gt;.boolCondition"></a>allConditions.&lt;list of mappings&gt;.boolCondition

Tests session variable containing boolean value.

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Variable name. |
| value | boolean | Expected value. |

### <a id="allConditions.&lt;list of mappings&gt;.intCondition"></a>allConditions.&lt;list of mappings&gt;.intCondition

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

### <a id="allConditions.&lt;list of mappings&gt;.stringCondition"></a>allConditions.&lt;list of mappings&gt;.stringCondition

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

### <a id="boolCondition"></a>boolCondition

Tests session variable containing boolean value.

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Variable name. |
| value | boolean | Expected value. |

### <a id="intCondition"></a>intCondition

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

### <a id="stringCondition"></a>stringCondition

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

### <a id="stringCondition.length"></a>stringCondition.length

| Property | Type | Description |
| ------- | ------- | ------- |
| equalTo | [Builder](#stringconditionlengthequalto) | Compared variable must be equal to this value. |
| greaterOrEqualTo | [Builder](#stringconditionlengthgreaterorequalto) | Compared variable must be greater or equal to this value. |
| greaterThan | [Builder](#stringconditionlengthgreaterthan) | Compared variable must be greater than this value. |
| lessOrEqualTo | [Builder](#stringconditionlengthlessorequalto) | Compared variable must be lower or equal to this value. |
| lessThan | [Builder](#stringconditionlengthlessthan) | Compared variable must be lower than this value. |
| notEqualTo | [Builder](#stringconditionlengthnotequalto) | Compared variable must not be equal to this value. |

### <a id="stringCondition.length.equalTo"></a>stringCondition.length.equalTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="stringCondition.length.greaterOrEqualTo"></a>stringCondition.length.greaterOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="stringCondition.length.greaterThan"></a>stringCondition.length.greaterThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="stringCondition.length.lessOrEqualTo"></a>stringCondition.length.lessOrEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="stringCondition.length.lessThan"></a>stringCondition.length.lessThan


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |

### <a id="stringCondition.length.notEqualTo"></a>stringCondition.length.notEqualTo


| Inline definition |
| -------- |
| Uses the argument as a constant value. |

| Property | Type | Description |
| ------- | ------- | ------- |
| fromVar | String | Input variable name. |
| value | int | Value (integer). |


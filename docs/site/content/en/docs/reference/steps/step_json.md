---
title: "json"
description: "Parse JSON in variable into another variable."
---
Parse JSON in variable into another variable.

| Property | Type | Description |
| ------- | ------- | -------- |
| delete | boolean | If this is set to true, the selected key will be deleted from the JSON and the modified JSON will be passed to the <code>processor</code>. |
| format | enum | Conversion to apply on the matching parts with 'toVar' or 'toArray' shortcuts.<br>Options:<ul><li><code>BYTEBUF</code>Store the buffer directly. Beware that this may cause memory leaks!</li><li><code>BYTES</code>Store data as byte array.</li><li><code>STRING</code>Interprets the bytes as UTF-8 string.</li></ul> |
| fromVar | String | Variable to load JSON from. |
| processor | [Processor.Builder](index.html#processors) | Add one or more processors. |
| query | String | Query selecting the part of JSON. |
| replace | [Transformer.Builder](#replace) | Custom transformation executed on the value of the selected item. Note that the output value must contain quotes (if applicable) and be correctly escaped. |
| replace (alternative)| String | Replace value of selected item with value generated through a <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>. Note that the result must contain quotes and be correctly escaped. |
| toArray | String | Shortcut to store selected parts in an array in the session. Must follow the pattern <code>variable[maxSize]</code> |
| toVar | String | Shortcut to store first match in given variable. Further matches are ignored. |
| unquote | boolean | Automatically unquote and unescape the input values. By default true. |

### replace

Custom transformation executed on the value of the selected item. Note that the output value must contain quotes (if applicable) and be correctly escaped.

| Property | Type | Description |
| ------- | ------- | ------- |
| actions | [ActionsTransformer.Builder](#replaceactions) | This transformer stores the (defragmented) input into a variable, using requested format. After that it executes all the actions and fetches transformed value using the pattern. |
| pattern | [Pattern.TransformerBuilder](#replacepattern) | Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing session variables. |

### replace.actions

This transformer stores the (defragmented) input into a variable, using requested format. After that it executes all the actions and fetches transformed value using the pattern.

| Property | Type | Description |
| ------- | ------- | ------- |
| actions | [Action.Builder](index.html#actions) | Actions to be executed. |
| format | enum | Format into which should this transformer convert the buffers before storing. Default is <code>STRING</code>.<br>Options:<ul><li><code>BYTEBUF</code>Store the buffer directly. Beware that this may cause memory leaks!</li><li><code>BYTES</code>Store data as byte array.</li><li><code>STRING</code>Interprets the bytes as UTF-8 string.</li></ul> |
| pattern | String | <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">Pattern</a> to use when fetching the transformed value. |
| var | String | Variable used as the intermediate storage for the data. |

### replace.pattern

Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing session variables.


| Inline definition |
| -------- |
| The pattern formatting string. |

| Property | Type | Description |
| ------- | ------- | ------- |
| pattern | String | Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing session variables. |


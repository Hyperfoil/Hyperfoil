---
title: "randomCsvRow"
description: "Stores random row from a CSV-formatted file to variables."
---
Stores random row from a CSV-formatted file to variables.

| Property | Type | Description |
| ------- | ------- | -------- |
| columns | [Builder](#columns) | Defines mapping from columns to session variables. |
| file | String | Path to the CSV file that should be loaded. |
| separator | char | Set character used for column separation. By default it is comma (<code>,</code>). |
| skipComments | boolean | Skip lines starting with character '#'. By default set to false. |

### columns

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;any&gt; | String | Use 0-based column as the key and variable name as the value. |


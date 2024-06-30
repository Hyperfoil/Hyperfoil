---
title: Reference
description: List of all possible steps and handlers that can be used for the benchmark development
weight: 6
---

Before exploring this reference you should be familiar with the [basic structure of a benchmark](/docs/user-guide/benchmark/). If you're not sure what is the difference between the phase, scenario and sequence check out the [concepts in user guide](/docs/overview/concepts/).

This reference lists all the steps and handlers used in a scenario. The lists below are not finite; you can also easily [develop and use your own components](/docs/getting-started/quickstart8/), but Hyperfoil provides generic components for the most common tasks out of the box.

There are few generic types of components:
* [steps](/docs/reference/steps/)
* [actions](/docs/reference/actions/)
* [processors](/docs/reference/processors/)

This documentation is auto-generated from Javadoc in source code, explaining format for each key-value pair in benchmark YAML. If there is an issue with these docs (e.g. property showing no description) please [file an issue on GitHub](https://github.com/Hyperfoil/Hyperfoil/issues/new).

This is the basic structure of the docs:

***
EXAMPLE

Example description.

| Property | Type  | Description |
| -------- | ----- | ------------|
| Key      | Class | Explanation for the value  |

***

YAML syntax
```
EXAMPLE:
  Key: Value
```

For example, the POST definition in [httpRequest step](/docs/reference/steps/step_httpRequest) looks like this:

***

POST

Generic builder for generating a string.

| Property | Type   | Description |
| -------- | ------ | ----------- |
| fromVar  | String | Load the string from session variable.  |
| pattern  | String | Use pattern replacing session variables.  |
| value    | String | String value used verbatim.  |

***

YAML syntax
```
POST:
  pattern: /user/${userId}/info
```

You might be wondering why the documentation above does not mention anything about issuing a HTTP request. In fact the top-level `POST` property [httpRequest](/docs/reference/steps/step_httpRequest) says "Issue HTTP POST request to given path." but the `POST()` method returns a generic string builder; this generic builder is used as the path for the HTTP request with POST method.

If the 'type' is not a scalar value, the key in 'property' works as a link to further property mapping. It's also possible that the property has multiple options, e.g. accepting both property mapping and list of values.

For brevity some components have inline definition like this:

***

SET

Set variable in session to certain value.

| Inline definition |
| ----------------- |
| Use `var <- value`. |

***

YAML syntax
```yaml
set: myVar <- "This is my value"
```
&nbsp;

Hyperfoil defines automatically generated [JSON schema](/schema.json) for the benchmark; you can [use that in Visual Studio Code](/docs/howtos/editor/) to automatically check the YAML syntax.


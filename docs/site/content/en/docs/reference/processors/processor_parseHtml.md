---
title: "parseHtml"
description: "Parses HTML tags and invokes handlers based on criteria."
---
Parses HTML tags and invokes handlers based on criteria.

| Property | Type | Description |
| ------- | ------- | -------- |
| onEmbeddedResource | [Builder](#onembeddedresource) | Handler firing upon reference to other resource, e.g. image, stylesheet... |
| onTagAttribute | [Builder](#ontagattribute) | <font color="#606060">&lt;no description&gt;</font> |

### <a id="onEmbeddedResource"></a>onEmbeddedResource

Handles <code>&lt;img src="..."&gt;</code>, <code>&lt;link href="..."&gt;</code>, <code>&lt;embed src="..."&gt;</code>, <code>&lt;frame src="..."&gt;</code>, <code>&lt;iframe src="..."&gt;</code>, <code>&lt;object data="..."&gt;</code> and <code>&lt;script src="..."&gt;</code>. 

 Does not handle <code>&lt;source src="..."&gt;</code> or <code>&lt;track src="..."&gt;</code> because browser would choose only one of the options.

| Property | Type | Description |
| ------- | ------- | ------- |
| fetchResource | [Builder](#onembeddedresourcefetchresource) | Automatically download referenced resource. |
| ignoreExternal | boolean | Ignore resources hosted on servers that are not covered in the <code>http</code> section. |
| processor | [Processor.Builder](index.html#processors) | Add one or more processors. |
| processor (alternative)| [Processor.Builder](index.html#processors) | Custom processor invoked pointing to attribute data - e.g. in case of <code>&lt;img&gt;</code> tag the processor gets contents of the <code>src</code> attribute. |

### <a id="onEmbeddedResource.fetchResource"></a>onEmbeddedResource.fetchResource

Automates download of embedded resources.

| Property | Type | Description |
| ------- | ------- | ------- |
| concurrency | int | Maximum number of resources fetched concurrently. Default is 8. |
| maxResources | int | Maximum number of resources that can be fetched. |
| metric | [&lt;list of strings&gt;](#onembeddedresourcefetchresourcemetric) | Metrics selector for downloaded resources. |
| onCompletion | [Action.Builder](index.html#actions) | Action performed when the download of all resources completes. |

### <a id="onEmbeddedResource.fetchResource.metric"></a>onEmbeddedResource.fetchResource.metric

Metrics selector for downloaded resources.

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;list of strings&gt; | &lt;list of strings&gt; | {::nomarkdown}Allows categorizing request statistics into metrics based on the request path. The expressions are evaluated in the order as provided in the list. Use one of: <ul> <li><code>regexp -&gt; replacement</code>, e.g. <code>([^?]*)(\?.*)? -&gt; $1</code> to drop the query part. <li><code>regexp</code> (don't do any replaces and use the full path), e.g. <code>.*.jpg</code> <li><code>-&gt; name</code> (metric applied if none of the previous expressions match). </ul>{:/} |

### <a id="onTagAttribute"></a>onTagAttribute

| Property | Type | Description |
| ------- | ------- | ------- |
| attribute | String | Name of the attribute in this element you want to process, e.g. <code>action</code> |
| format | enum | Conversion to apply on the matching parts with 'toVar' or 'toArray' shortcuts.<br>Options:{::nomarkdown}<ul><li><code>BYTEBUF</code>: {:/}Store the buffer directly. Beware that this may cause memory leaks!{::nomarkdown}</li><li><code>BYTES</code>: {:/}Store data as byte array.{::nomarkdown}</li><li><code>STRING</code>: {:/}Interprets the bytes as UTF-8 string.{::nomarkdown}</li></ul>{:/} |
| processor | [Processor.Builder](index.html#processors) | Add one or more processors. |
| tag | String | Name of the tag this handler should look for, e.g. <code>form</code> |
| toArray | String | Shortcut to store selected parts in an array in the session. Must follow the pattern <code>variable[maxSize]</code> |
| toVar | String | Shortcut to store first match in given variable. Further matches are ignored. |


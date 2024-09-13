---
title: JSON schema support
description: How to make your life easier while building your own benchmarks
categories: [HowTo]
tags: [how-to, guides, editor, json-schema]
weight: 1
---

For your convenience we recommend using editor with YAML validation against JSON schema; you can point your editor to `docs/schema.json`. We can recommend [Visual Studio Code](https://code.visualstudio.com/)
with [redhat.vscode-yaml](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml) plugin.


You need to edit settings file to map benchmark configuration files (with `.hf.yaml` extension) to the schema, adding:
```json
"yaml.schemas" : {
    "file:///path/to/hyperfoil-distribution/docs/schema.json" : "/*.hf.yaml"
},
```

Note that you can also directly point to the hosted JSON schema definition:
```json
"yaml.schemas" : {
    "https://hyperfoil.io/schema.json" : "/*.hf.yaml"
},
```
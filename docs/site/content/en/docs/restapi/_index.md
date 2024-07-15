---
title: Controller API
description: OpenAPI 3 specification of the Hyperfoil controller
weight: 8
---

As a user you'll probably interact with the Controller through CLI. However when you set up e.g. regression runs in CI you'll need to control the test programmatically. Some limited capabilities are already exposed through [Ansible Galaxy scripts](/docs/user-guide/installation/ansible/) but to get full control you can use the REST API - the same as the CLI or Ansible scripts connect to.

Hyperfoil Controller API is defined through [OpenAPI 3 Specification](https://swagger.io/docs/specification/about/). Our [OpenAPI reference](https://github.com/Hyperfoil/Hyperfoil/blob/master/controller-api/src/main/resources/openapi.yaml) defines some types as free-form objects (e.g. the benchmark definition); refer to source code in these cases.


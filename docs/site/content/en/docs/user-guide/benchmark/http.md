---
title: HTTP
description: >
  This section defines servers that agents contact during benchmarks, 
  allowing configurations for multiple targets with specific connection settings
categories: [Guide, Benchmark]
tags: [guides, benchmark, http]
weight: 2
---

All servers that Hyperfoil should contact must be declared in this section. Before the benchmark starts Hyperfoil agents will open connections to the target servers; if this connection fails the benchmark is terminated immediatelly.

You can either declare single target server (the default one) within this section or more of them:

```yaml
http:
  host: http://example.com
  ...
```

```yaml
http:
- host: http://example.com
  sharedConnections: 100
- host: http://example.com:8080
  sharedConnections: 50
```

HTTP configuration has these properties:

| Property          | Default | Description |
| ----------------- | ------- | ----------- |
| protocol          |         | Either `http` or `https` |
| host              |         | Hostname of the server. For convenience you can use the `http[s]://host[:port]` inline syntax as shown above |
| port              | `80`&nbsp;or&nbsp;`443` | Default is based on the `protocol` |
| [sharedConnections](#shared-connections) | 1       | Number of connections to open. It is recommended to set this property to a non-default value. |
| [connectionStrategy](#connection-strategies) | SHARED_POOL | Connection pooling model (see details below) |
| addresses         |         | Supply list of IPs or IP:port targets that will be used for the connections instead of resolving the `host` in DNS and using `port` as set - `host` and `port` will be used only for `Host` headers and SNI. If this list contains more addresses the connections will be split evenly. |
| requestTimeout    | 30 seconds | Default request timeout, this can be overridden in each `httpRequest`. |
| allowHttp1x       | true    | Allow HTTP 1.1 for connections (e.g. during ALPN). |
| allowHttp2x       | true    | Allow HTTP 2.0 for connections (e.g. during ALPN). If both 1.1 and 2.0 are allowed and `https` is not used (which would trigger ALPN) Hyperfoil will use HTTP 1.1. If only 2.0 is allowed Hyperfoil will start with HTTP 1.1 and perform protocol upgrade to 2.0. |
| directHttp2       | false   | Start with H2C HTTP 2.0 without protocol upgrade. Makes sense only for plain text (`http`) connections. Currently not implemented. |
| maxHttp2Streams   | 100     | Maximum number of requests concurrently enqueued on single HTTP 2.0 connection. |
| pipeliningLimit   | 1       | Maximum number of requests pipelined on single HTTP 1.1 connection. |
| rawBytesHandlers  | true    | Enable or disable using handlers that process HTTP response raw bytes. |
| [keyManager](#keymanager-configuration) |         | TLS key manager for setting up client certificates. |
| [trustManager](#trustmanager-configuration) |         | TLS trust manager for setting up server certificates. |
| useHttpCache      | true    | Make use of HTTP cache on client-side. If multiple authorities are involved, disable the HTTP cache for all of them to achieve the desired outcomes. The default is `true` except for wrk/wrk2 wrappers where it is set to `false`. |

## Shared connections

This number is split between all agents and executor threads evenly; if there are too many agents/executors each will get at least 1 connection.

When a scalar value is used for this property the connection pool has fixed size; Hyperfoil opens all connections when the benchmark starts and should a connection be closed throughout the benchmark, another connection is reopened instead. You can change this behaviour by composing the property of these sub-properties:

| Property      | Description |
| ------------- | ----------- |
| core          | Number of connections that will be opened when the benchmark starts. Number of connections in the pool should never drop below this value (another connection will be opened instead). |
| max           | Maximum number of connections in the pool. |
| buffer        | Hyperfoil will try to keep at least `active + buffer` connections in the pool where `active` is the number of currently used connection (those with at least 1 in-flight request) |
| keepAliveTime | When a connection is not used for more than this value (in milliseconds) it will be closed. Non-positive value means that the connection is never closed because of being idle. |

Example:

```yaml
http:
  host: http://example.com
  sharedConnections:
    core: 10
    buffer: 10
    max: 10
    keepAliveTime: 30000
```

## Connection strategies

This property describes the connection pooling model, you can choose from the options below:

| Strategy        | Description |
| --------------- | ----------- |
| SHARED_POOL     | Connections are created in a pool and then borrowed by the session. When the request is complete the connection is returned to the shared pool. |
| SESSION_POOLS   | Connections are created in a shared pool. When the request is completed it is not returned to the shared pool but to a session-local pool. Subsequent requests by this session first try to acquire the connection from this local pool. When the session completes all connections from the session-local pool are returned to the shared pool.
| OPEN_ON_REQUEST | Connections are created before request or borrowed from a session-local pool. When the request is completed the connection is returned to this pool. When the session completes all connections from the session-local pool are closed. |
| ALWAYS_NEW      | Always create the connection before the request and close it when it is complete. No pooling of connections. |

## KeyManager configuration

All files are loaded when the benchmark is constructed, e.g. on the machine running CLI. You don't need to upload any files to controller or agent machines.

| Property  | Default | Description |
| --------- | ------- | ----------- |
| storeType | JKS     | Implementation of the store. |
| storeFile |         | Path to a file with the store. |
| password  |         | Password for accessing the store file. |
| alias     |         | Keystore alias. |
| certFile  |         | Path to a file with the client certificate. |
| keyFile   |         | Path to a file with client's private key. |

## TrustManager configuration

All files are loaded when the benchmark is constructed, e.g. on the machine running CLI. You don't need to upload any files to controller or agent machines.

| Property  | Default | Description |
| --------- | ------- | ----------- |
| storeType | JKS     | Implementation of the store. |
| storeFile |         | Path to a file with the store. |
| password  |         | Password for accessing the store file. |
| certFile  |         | Path to a file with the server certificate. |

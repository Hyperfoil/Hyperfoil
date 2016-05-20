## Usage

```
> mvn package
```

then

```
> mvn -P$profile exec:exec
```

Where $profile is:

- `vertx`
- `netty`
- `undertow`
- `jetty`

Or

```
> java -javaagent:/path/to/alpn/agent -jar target/http2-bench-3.3.0-SNAPSHOT.jar $profile
```

where $profile is:

- `vertx`
- `netty`
- `undertow`
- `jetty`

Each server has special options, `netty` and `vertx` can run without the ALPN agent when the `--open-ssl` option is set.

Servers can run with diffent backend with the `--backend` option:

- noop (default)
- disk
- db (postgres)
- microservice

A microservice can also be ran for the backend:

```
java -jar target/http2-bench-3.3.0-SNAPSHOT.jar client -r 12000 -d 30 -w 5 -c 500 -m 10 https://localhost:8443
```

This is a noop service pausing for 20ms.

## Stressing

Use the provided client:

```
java -jar target/http2-bench-3.3.0-SNAPSHOT.jar client -r 12000 -d 30 -w 5 -c 500 -m 10 https://localhost:8443
```


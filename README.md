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
- `vertx-openssl`
- `netty`
- `netty-openssl`
- `undertow`
- `jetty`

Or

```
> java -javaagent:/path/to/alpn/agent -jar target/http2-bench-3.3.0-SNAPSHOT.jar $cmd
```

where $profile is:

- `vertx`
- `netty`
- `undertow`
- `jetty`

Each server has special options, `netty` and `vertx` can run without the ALPN agent when the `--open-ssl` option is set.

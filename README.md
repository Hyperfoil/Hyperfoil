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

## Stressing

### POST 256 bytes

````
mkfile 1b tiny_payload
h2load -n100000 -c200 -m10 -d tiny_payload -v  https://localhost:8443/
````

### POST 50 MB

````
mkfile 50m large_payload
h2load -n1000 -c100 -m 1 -d large_file https://localhost:8443/
````


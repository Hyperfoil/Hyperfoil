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
> java -javaagent:/path/to/alpn/agent -jar target/http2-bench-1.0.0-SNAPSHOT.jar $profile
```

where _$profile_ is one of:

- `vertx`
- `netty`
- `undertow`
- `jetty`

Each server has special options, `netty` and `vertx` can run without the ALPN agent when the `--open-ssl` option is set.

Servers can run with diffent backend with the `--backend` option:

- noop (default)
- disk
- db (postgres)
- a backend

for instance:

```
java -jar target/http2-bench-1.0.0-SNAPSHOT.jar vertx --open-ssl --backend http
```

An http backend can also be ran for the backend:

```
java -jar target/http2-bench-1.0.0-SNAPSHOT.jar http-backend --delay 20
```

This is a noop service pausing for 20ms.

## Stressing

Use the provided client:

```
# 1000 r/s for 30 seconds using 100 connections and at most 10 requests per connection with a warmup of 5 seconds
java -jar target/http2-bench-1.0.0-SNAPSHOT.jar http-client -w 5 -r 1000 -d 30 -c 100 -q 10 https://localhost:8443
```

## Charting

```
java -jar target/http2-bench-1.0.0-SNAPSHOT.jar http-client -w 5 -r 1000 1500 2000 2500 3000 3500 4000 4500 5000  -d 30 -c 100 -q 10 --out vertx http://192.168.0.247:8443
```

```
gnuplot -e "out='vertx'" gnuplot.plg
```

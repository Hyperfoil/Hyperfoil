## Usage

```
> mvn compile
```

then

```
> mvn -P$profile exec:exec
```

where $profile is:

- `vertx`
- `vertx-openssl`
- `netty`
- `netty-openssl`

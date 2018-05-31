
## HTTP client benchmark

```
> java -jar target/http2-bench-1.0.0-SNAPSHOT.jar http-client -c 256 -t 4 -p HTTP_1_1 -d 10m -r 50100 --provider vertx http://localhost:8080/1m.dat
```


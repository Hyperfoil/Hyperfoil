
## HTTP client benchmark

```
> java -jar target/http2-bench-1.0.0-SNAPSHOT.jar http-client -c 256 -t 4 -p HTTP_1_1 -d 10m -r 1000 --provider vertx https://localhost:8080/10k.dat
```

```
> java -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=alloc-recording.jfr,settings=gc.jfc -jar target/http2-bench-1.0.0-SNAPSHOT.jar http-client -c 256 -t 1 -p HTTP_1_1 -d 10m -r 1000 --provider vertx https://localhost:8080/10k.dat
```
Build container with tc installed

```
> docker build -t test:nginx nginx
```

Running the container

```
> docker run --rm --name test-nginx -p 8080:80 --cap-add=NET_ADMIN test:nginx
```

Add one 1 ms latency to eth0

```
> docker exec -it test-nginx tc qdisc add dev eth0 root netem delay 1ms
```

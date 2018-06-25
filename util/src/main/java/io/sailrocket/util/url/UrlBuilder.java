package io.sailrocket.util.url;

import java.util.function.Consumer;

public class UrlBuilder {

    private String prot;
    private int port;
    private String host;
    private String path;

    private UrlBuilder() {
    }

    public static UrlBuilder builder() {
        return new UrlBuilder();
    }

    private UrlBuilder apply(Consumer<UrlBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    public UrlBuilder protocol(String prot) {
        return apply(clone -> clone.prot = prot);
    }

    public UrlBuilder port(int port) {
        return apply(clone -> clone.port = port);
    }

    public UrlBuilder host(String host) {
        return apply(clone -> clone.host = host);
    }

    public UrlBuilder path(String path) {
        return apply(clone -> clone.path = path);
    }

    public Url build() {
        return new Url(prot, host, path, port);
    }

}

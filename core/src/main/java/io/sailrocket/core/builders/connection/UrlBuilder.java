package io.sailrocket.core.builders.connection;

import java.util.function.Consumer;

public class UrlBuilder {

    private String prot;
    private int port;
    private String host;
    private String path;
    private String url;

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

    public UrlBuilder url(String url) {
        return apply(clone -> clone.url = url);
    }

    public Url build() {
        if(prot != null && port > 0 && host != null && path != null)
            return new Url(prot, host, path, port);
        else
            return new Url(url);
    }

}

package io.sailrocket.core.builders.connection;

import io.sailrocket.api.http.HttpVersion;

public class HttpProtocol implements Protocol {

    private final int port;
    private final HttpVersion version;

    public HttpProtocol() {
        this(-1, HttpVersion.HTTP_1_1);
    }

    public HttpProtocol(int port) {
        this(port, HttpVersion.HTTP_1_1);
    }

    public HttpProtocol(int port, HttpVersion version) {
        this.port = port >= 0 ? port : 80;
        this.version = version;
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public boolean secure() {
        return false;
    }

    @Override
    public HttpVersion version() {
        return version;
    }

    @Override
    public int port() {
        return port;
    }
}

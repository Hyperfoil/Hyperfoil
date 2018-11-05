package io.sailrocket.core.builders.connection;

import io.sailrocket.api.http.HttpVersion;

public class HttpsProtocol implements Protocol {

    private final int port;
    private final HttpVersion version;

    public HttpsProtocol() {
        this(-1, HttpVersion.HTTP_1_1);
    }

    public HttpsProtocol(int port) {
        this(port, HttpVersion.HTTP_1_1);
    }

    public HttpsProtocol(int port, HttpVersion version) {
        this.port = port >= 0 ? port : 443;
        this.version = version;
    }

    @Override
    public String name() {
        return "https";
    }

    @Override
    public boolean secure() {
        return true;
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

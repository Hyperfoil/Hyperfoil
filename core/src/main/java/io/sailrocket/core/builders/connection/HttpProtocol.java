package io.sailrocket.core.builders.connection;

public class HttpProtocol implements Protocol {

    private final int port;
    private final Version version;

    public HttpProtocol() {
        this(-1, Version.HTTP_1_1);
    }

    public HttpProtocol(int port) {
        this(port, Version.HTTP_1_1);
    }

    public HttpProtocol(int port, Version version) {
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
    public Version version() {
        return version;
    }

    @Override
    public int port() {
        return port;
    }
}

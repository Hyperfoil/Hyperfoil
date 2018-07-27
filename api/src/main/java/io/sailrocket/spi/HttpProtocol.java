package io.sailrocket.spi;

public class HttpProtocol implements Protocol {

    private final int port;
    private final Version version;

    public HttpProtocol() {
        this.port = 80;
        this.version = Version.HTTP_1_1;
    }

    public HttpProtocol(int port) {
        this.port = port;
        this.version = Version.HTTP_1_1;
    }

    public HttpProtocol(int port, Version version) {
        this.port = port;
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

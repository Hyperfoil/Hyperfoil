package io.sailrocket.spi;

public class HttpsProtocol implements Protocol {

    private final int port;
    private final Version version;

    public HttpsProtocol() {
        this.port = 443;
        this.version = Version.HTTP_1_1;
    }

    public HttpsProtocol(int port) {
        this.port = port;
        this.version = Version.HTTP_1_1;
    }

    public HttpsProtocol(int port, Version version) {
        this.port = port;
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
    public Version version() {
        return version;
    }

    @Override
    public int port() {
        return port;
    }
}

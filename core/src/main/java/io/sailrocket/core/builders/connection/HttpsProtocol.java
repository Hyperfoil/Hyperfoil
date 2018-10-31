package io.sailrocket.core.builders.connection;

public class HttpsProtocol implements Protocol {

    private final int port;
    private final Version version;

    public HttpsProtocol() {
        this(-1, Version.HTTP_1_1);
    }

    public HttpsProtocol(int port) {
        this(port, Version.HTTP_1_1);
    }

    public HttpsProtocol(int port, Version version) {
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
    public Version version() {
        return version;
    }

    @Override
    public int port() {
        return port;
    }
}

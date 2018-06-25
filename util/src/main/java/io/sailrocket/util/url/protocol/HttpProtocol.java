package io.sailrocket.util.url.protocol;

public class HttpProtocol implements Protocol {

    private final int port;

    public HttpProtocol() {
        this.port = 80;
    }

    public HttpProtocol(int port) {
        this.port = port;
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
    public int port() {
        return port;
    }
}

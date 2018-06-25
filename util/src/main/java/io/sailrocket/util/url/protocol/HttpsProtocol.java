package io.sailrocket.util.url.protocol;

public class HttpsProtocol implements Protocol {

    private final int port;

    public HttpsProtocol() {
        this.port = 443;
    }

    public HttpsProtocol(int port) {
        this.port = port;
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
    public int port() {
        return port;
    }
}

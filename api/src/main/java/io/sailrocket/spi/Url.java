package io.sailrocket.spi;

import io.sailrocket.util.url.protocol.Protocol;
import io.sailrocket.util.url.protocol.Protocols;

import java.net.MalformedURLException;
import java.net.URL;

public class Url {

    private final Protocol protocol;
    private final String host;
    private final String path;

    public Url(String protocol, String host, String path, int port) {
        this.protocol = Protocols.protocol(protocol, port);
        this.host = host;
        this.path = path;
    }


    public Protocol protocol() {
        return protocol;
    }

    public String host() {
        return host;
    }

    public String path() {
        return path;
    }

    public URL toURL() throws MalformedURLException {
        return new URL(toString());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol.name()).append("://").append(host).append(protocol.port());
        if(path != null) {
            sb.append("/").append(path);
        }

        return sb.toString();
    }
}

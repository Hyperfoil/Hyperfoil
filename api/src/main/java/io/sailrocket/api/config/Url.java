package io.sailrocket.api.config;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Url implements Serializable {

    private Protocol protocol;
    private String host;
    private int port;
    private String path;

    public Url(String scheme, String host, String path, int port) {
        this.protocol = Protocol.fromScheme(scheme);
        this.host = host;
        this.port = this.protocol.portOrDefault(port);
        this.path = path;
    }

    public Url(String path) {
        try {
            URI uri = new URI(path);
            this.protocol = Protocol.fromScheme(uri.getScheme());
            this.host = uri.getHost();
            this.port = uri.getPort();
            this.path = uri.getPath();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Method value is not a correct url"+e.getMessage());
        }
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

    public int port() {
        return port;
    }

    public URL toURL() throws MalformedURLException {
        return new URL(toString());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol.scheme).append("://").append(host).append(":").append(port).append("/");
        if(path != null && !path.equals("/")) {
            sb.append(path);
        }

        return sb.toString();
    }
}

package io.sailrocket.core.builders.connection;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Url {

    private Protocol protocol;
    private String host;
    private String path;

    public Url(String protocol, String host, String path, int port) {
        this.protocol = Protocols.protocol(protocol, port);
        this.host = host;
        this.path = path;
    }

    public Url(String path) {
        try {
            URI uri = new URI(path);
            this.host = uri.getHost();
            this.path = uri.getPath();
            this.protocol = Protocols.protocol(uri.getScheme(), uri.getPort());
        }
        catch(URISyntaxException e) {
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

    public URL toURL() throws MalformedURLException {
        return new URL(toString());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol.name()).append("://").append(host).append(":").append(protocol.port()).append("/");
        if(path != null && !path.equals("/")) {
            sb.append(path);
        }

        return sb.toString();
    }
}

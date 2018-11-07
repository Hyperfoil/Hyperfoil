package io.sailrocket.core.builders.connection;

import java.util.stream.Stream;

public enum Protocol {
    HTTP("http", 80, false),
    HTTPS("https", 443, true);

    public final String scheme;
    public final int defaultPort;
    public final boolean secure;

    Protocol(String scheme, int defaultPort, boolean secure) {
        this.scheme = scheme;
        this.defaultPort = defaultPort;
        this.secure = secure;
    }

    public static Protocol fromScheme(String scheme) {
        return Stream.of(values()).filter(p -> p.scheme.equals(scheme)).findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown scheme '"+ scheme + "'"));
    }

    public int portOrDefault(int port) {
        return port < 0 ? defaultPort : port;
    }

    public boolean secure() {
        return secure;
    }
}

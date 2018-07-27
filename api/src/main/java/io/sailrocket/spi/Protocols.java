package io.sailrocket.spi;

public class Protocols {

    public static Protocol protocol(String protocol) {
        if(protocol.equalsIgnoreCase("http"))
            return new HttpProtocol();
        else if(protocol.equalsIgnoreCase("https"))
            return new HttpsProtocol();
        else
            throw new IllegalArgumentException("Protocol "+protocol+" not supported");
    }

    public static Protocol protocol(String protocol, int port) {
        if(protocol.equalsIgnoreCase("http"))
            return new HttpProtocol(port);
        else if(protocol.equalsIgnoreCase("https"))
            return new HttpsProtocol(port);
        else
            throw new IllegalArgumentException("Protocol "+protocol+" not supported");
    }


}

package io.sailrocket.util.url.protocol;

public interface Protocol {

    String name();

    boolean secure();

    int port();

}

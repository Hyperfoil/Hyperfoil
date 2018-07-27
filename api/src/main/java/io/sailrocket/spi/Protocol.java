package io.sailrocket.spi;

public interface Protocol {

    String name();

    boolean secure();

    Version version();

    int port();

}

package io.sailrocket.core.builders.connection;

public interface Protocol {

    String name();

    boolean secure();

    Version version();

    int port();

}

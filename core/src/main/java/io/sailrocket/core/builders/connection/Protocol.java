package io.sailrocket.core.builders.connection;

import io.sailrocket.api.http.HttpVersion;

public interface Protocol {

    String name();

    boolean secure();

    HttpVersion version();

    int port();

}

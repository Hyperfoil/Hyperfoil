package io.sailrocket.util.url.protocol;

public interface Protocol {

    public String name();

    public boolean secure();

    public int port();

}

package io.sailrocket.api.session;

public interface PhaseChangeHandler {
   void onChange(String phase, PhaseInstance.Status status, boolean successful);
}

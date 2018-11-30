package io.sailrocket.clustering;

import io.sailrocket.api.config.Phase;

public class ControllerPhase {
   private final Phase definition;
   private Status status = Status.NOT_STARTED;
   private long absoluteStartTime = Long.MIN_VALUE;
   private long absoluteTerminateTime = Long.MIN_VALUE;

   public ControllerPhase(Phase definition) {
      this.definition = definition;
   }

   public Phase definition() {
      return definition;
   }

   public Status status() {
      return status;
   }

   public long absoluteStartTime() {
      return absoluteStartTime;
   }

   public void status(Status status) {
      if (this.status.ordinal() < status.ordinal()) {
         this.status = status;
      }
   }

   public void absoluteStartTime(long time) {
      absoluteStartTime = time;
   }

   public long absoluteTerminateTime() {
      return absoluteTerminateTime;
   }

   public void absoluteTerminateTime(long absoluteTerminateTime) {
      this.absoluteTerminateTime = absoluteTerminateTime;
   }

   enum Status {
      NOT_STARTED,
      STARTING,
      RUNNING,
      FINISHING,
      FINISHED,
      TERMINATING,
      TERMINATED;

      public boolean isFinished() {
         return ordinal() >= FINISHED.ordinal();
      }
   }
}

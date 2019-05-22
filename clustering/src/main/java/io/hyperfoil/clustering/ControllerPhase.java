package io.hyperfoil.clustering;

import io.hyperfoil.api.config.Phase;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ControllerPhase {
   private static Logger log = LoggerFactory.getLogger(ControllerPhase.class);

   private final Phase definition;
   private Status status = Status.NOT_STARTED;
   private long absoluteStartTime = Long.MIN_VALUE;
   private long absoluteCompletionTime = Long.MIN_VALUE;
   private boolean failed;

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
         log.info("{} changing status to {}", definition.name, status);
         this.status = status;
      }
   }

   public void absoluteStartTime(long time) {
      absoluteStartTime = time;
   }

   public long absoluteCompletionTime() {
      return absoluteCompletionTime;
   }

   public void absoluteCompletionTime(long absoluteCompletionTime) {
      this.absoluteCompletionTime = absoluteCompletionTime;
   }

   public void setFailed() {
      this.failed = true;
   }

   public boolean isFailed() {
      return failed;
   }

   enum Status {
      NOT_STARTED,
      STARTING,
      RUNNING,
      FINISHING,
      FINISHED,
      TERMINATING,
      TERMINATED,
      CANCELLED;

      public boolean isFinished() {
         return ordinal() >= FINISHED.ordinal();
      }

      public boolean isTerminated() { return ordinal() >= TERMINATED.ordinal(); }
   }
}

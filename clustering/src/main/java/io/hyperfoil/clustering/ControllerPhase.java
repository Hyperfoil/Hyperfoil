package io.hyperfoil.clustering;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.GlobalData;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ControllerPhase {
   private static final Logger log = LogManager.getLogger(ControllerPhase.class);

   private final Phase definition;
   private Status status = Status.NOT_STARTED;
   private long absoluteStartTime = Long.MIN_VALUE;
   private long absoluteCompletionTime = Long.MIN_VALUE;
   private boolean failed;
   private Long delayStatsCompletionUntil = null;
   private Map<String, GlobalData.Accumulator> globalData = new HashMap<>();

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

   public void status(String runId, Status status) {
      if (this.status.ordinal() < status.ordinal()) {
         log.info("{} {} changing status {} to {}", runId, definition.name, this.status, status);
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

   public Long delayStatsCompletionUntil() {
      return delayStatsCompletionUntil;
   }

   public void setFailed() {
      this.failed = true;
   }

   public boolean isFailed() {
      return failed;
   }

   public void delayStatsCompletionUntil(long time) {
      delayStatsCompletionUntil = delayStatsCompletionUntil == null ? time : Math.max(time, delayStatsCompletionUntil);
   }

   public void addGlobalData(Map<String, GlobalData.Element> data) {
      if (data == null) {
         return;
      }
      for (var entry : data.entrySet()) {
         log.debug("Received global data {} -> {}", entry.getKey(), entry.getValue());
         GlobalData.Accumulator accumulator = globalData.get(entry.getKey());
         if (accumulator == null) {
            globalData.put(entry.getKey(), accumulator = entry.getValue().newAccumulator());
         }
         accumulator.add(entry.getValue());
      }
   }

   public Map<String, GlobalData.Element> completeGlobalData() {
      return globalData.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().complete()));
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

      public boolean isTerminated() {
         return ordinal() >= TERMINATED.ordinal();
      }

      public boolean isStarted() {
         return ordinal() >= RUNNING.ordinal();
      }
   }
}

package io.hyperfoil.core.jfr;

import jdk.jfr.*;

@Name("io.hyperfoil.ScheduledFireTimeEvent")
@Label("Fire Time Sequence Event")
@Description("Event fired when a fire time sequence is scheduled")
@Category({ "Hyperfoil", "PhaseInstance" })
@Enabled(false)
@StackTrace(false)
public class ScheduledFireTimeEvent extends Event {

   private static final EventType EVENT_TYPE = EventType.getEventType(ScheduledFireTimeEvent.class);

   @Label("ID")
   public long id;

   @Label("Phase definition")
   public String phaseDef;

   @Label("Run ID")
   public String runId;

   @Label("Agent ID")
   public int agentId;

   @Label("PhaseInstance start time (ns)")
   public long startTimeNs;

   @Label("Scheduling Thread")
   public Thread schedulingThread;

   @Label("Throttled Sessions")
   public boolean throttledSessions;

   @Label("Actual Fire Time")
   public long actualFireTime;

   @Label("Scheduling Time")
   public long schedulingTime;

   @Label("Intended Fire Time (ns)")
   public long currentFireTimeNs;

   @Label("Next Intended Fire Time (ns)")
   public long nextFireTimeNs;

   public static boolean isEventEnabled() {
      return EVENT_TYPE.isEnabled();
   }

   public static void fire(
         long instanceId,
         String phaseDef,
         String runId,
         int agentId,
         long startTimeNs,
         long actualFireTime, long schedulingTime,
         long currentFireTimeNs,
         long nextFireTimeNs, boolean throttledSession) {
      var event = new ScheduledFireTimeEvent();
      event.id = instanceId;
      event.phaseDef = phaseDef;
      event.runId = runId;
      event.agentId = agentId;
      event.startTimeNs = startTimeNs;
      event.schedulingThread = Thread.currentThread();
      event.actualFireTime = actualFireTime;
      event.schedulingTime = schedulingTime;
      event.currentFireTimeNs = currentFireTimeNs;
      event.nextFireTimeNs = nextFireTimeNs;
      event.throttledSessions = throttledSession;
      event.commit();
   }
}

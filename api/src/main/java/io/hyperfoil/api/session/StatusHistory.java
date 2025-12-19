package io.hyperfoil.api.session;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class StatusHistory {

   public PhaseInstance.Status previousStatus;
   public PhaseInstance.Status currentStatus;
   public long when;
   public long threadId;

   public ZonedDateTime getWhenInstant() {
      return Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault());
   }

   @Override
   public String toString() {
      return "StatusHistory{" +
            "previousStatus=" + previousStatus +
            ", currentStatus=" + currentStatus +
            ", when=" + getWhenInstant() +
            ", threadId=" + threadId +
            '}';
   }
}

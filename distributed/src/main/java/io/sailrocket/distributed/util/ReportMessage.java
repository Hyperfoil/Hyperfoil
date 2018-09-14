package io.sailrocket.distributed.util;

import java.io.Serializable;
import java.util.Map;

import io.sailrocket.api.Report;

public class ReportMessage implements Serializable {
   private final String address;
   private final Map<String, Report> reports;

   public ReportMessage(String address, Map<String, Report> reports) {
      this.address = address;
      this.reports = reports;
   }

   public Map<String, Report> reports() {
      return reports;
   }

   public static class Codec extends ObjectCodec<ReportMessage> {}
}

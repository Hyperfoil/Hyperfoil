package io.hyperfoil.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.EncodableHistogram;
import org.HdrHistogram.HistogramLogReader;
import org.HdrHistogram.HistogramLogWriter;

import io.hyperfoil.controller.model.Histogram;

public final class HistogramConverter {
   private HistogramConverter() {
   }

   public static Histogram convert(String phase, String metric, AbstractHistogram source) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(source.getNeededByteBufferCapacity() + 100);
      HistogramLogWriter writer = new HistogramLogWriter(bos);
      writer.outputIntervalHistogram(source);
      writer.close();
      return new Histogram(phase, metric, source.getStartTimeStamp(), source.getEndTimeStamp(),
            bos.toString(StandardCharsets.UTF_8));
   }

   public static AbstractHistogram convert(Histogram source) {
      ByteArrayInputStream bis = new ByteArrayInputStream(source.data.getBytes(StandardCharsets.UTF_8));
      EncodableHistogram histogram = new HistogramLogReader(bis).nextIntervalHistogram();
      if (histogram == null) {
         return null;
      }
      histogram.setStartTimeStamp(source.startTime);
      histogram.setEndTimeStamp(source.endTime);
      return (AbstractHistogram) histogram;
   }
}

package io.hyperfoil.core.handlers;


import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Util;
import io.netty.buffer.ByteBuf;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class NewSequenceProcessor implements Processor<Request>, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(NewSequenceProcessor.class);
   private static final boolean trace = log.isTraceEnabled();

   private final int maxSequences;
   private final Access counterVar;
   private final Access dataVar;
   private final String sequence;

   public NewSequenceProcessor(int maxSequences, String counterVar, String dataVar, String sequence) {
      this.maxSequences = maxSequences;
      this.counterVar = SessionFactory.access(counterVar);
      this.dataVar = SessionFactory.access(dataVar);
      this.sequence = sequence;
   }

   @Override
   public void before(Request request) {
      counterVar.setInt(request.session, 0);
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (!isLastPart) {
         throw new IllegalArgumentException("This processor expects already defragmented data.");
      }
      Object obj = dataVar.getObject(request.session);
      if (!(obj instanceof ObjectVar[])) {
         throw new IllegalStateException(dataVar + " must be a sequence-scoped variable!");
      }
      int counter = counterVar.addToInt(request.session, 1);
      String value = Util.toString(data, offset, length);
      if (trace) {
         log.trace("Creating new sequence {}, id {}, value {}", sequence, counter, value);
      }
      ObjectVar[] vars = (ObjectVar[]) obj;
      vars[counter].set(value);
      request.session.phase().scenario().sequence(sequence).instantiate(request.session, counter);
   }

   @Override
   public void reserve(Session session) {
      counterVar.declareInt(session);
      dataVar.declareObject(session);
      dataVar.setObject(session, ObjectVar.newArray(session, maxSequences));
   }

   @MetaInfServices(Request.ProcessorBuilderFactory.class)
   public static class BuilderFactory implements Request.ProcessorBuilderFactory {
      @Override
      public String name() {
         return "newSequence";
      }

      @Override
      public boolean acceptsParam() {
         return false;
      }

      @Override
      public Builder newBuilder(Locator locator, String param) {
         return new Builder();
      }
   }

   public static class Builder implements Processor.Builder<Request> {
      private int maxSequences = -1;
      private String counterVar;
      private String dataVar;
      private String sequence;

      public Builder maxSequences(int maxSequences) {
         this.maxSequences = maxSequences;
         return this;
      }

      public Builder counterVar(String counterVar) {
         this.counterVar = counterVar;
         return this;
      }

      public Builder dataVar(String dataVar) {
         this.dataVar = dataVar;
         return this;
      }

      public Builder sequence(String sequence) {
         this.sequence = sequence;
         return this;
      }

      @Override
      public NewSequenceProcessor build() {
         if (maxSequences <= 0) {
            throw new BenchmarkDefinitionException("maxSequences is missing or invalid.");
         }
         if (counterVar == null) {
            throw new BenchmarkDefinitionException("Undefined counterVar");
         }
         if (dataVar == null) {
            throw new BenchmarkDefinitionException("Undefined dataVar");
         }
         if (sequence == null) {
            throw new BenchmarkDefinitionException("Undefined sequence template");
         }
         return new NewSequenceProcessor(maxSequences, counterVar, dataVar, sequence);
      }
   }
}

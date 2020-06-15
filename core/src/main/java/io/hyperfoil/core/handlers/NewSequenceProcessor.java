package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.RequestProcessorBuilder;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.util.Util;
import io.netty.buffer.ByteBuf;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class NewSequenceProcessor implements Processor {
   private static final Logger log = LoggerFactory.getLogger(NewSequenceProcessor.class);
   private static final boolean trace = log.isTraceEnabled();

   private final String sequence;
   private final Session.ConcurrencyPolicy policy;

   public NewSequenceProcessor(String sequence, Session.ConcurrencyPolicy policy) {
      this.sequence = sequence;
      this.policy = policy;
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (!isLastPart) {
         return;
      }
      if (trace) {
         String value = Util.toString(data, offset, length);
         log.trace("#{}, Creating new sequence {}, value (possibly incomplete) {}", session.uniqueId(),
               sequence, value);
      }
      session.startSequence(sequence, policy);
   }

   /**
    * Instantiates a sequence for each invocation. The sequences will have increasing sequence ID.
    */
   @MetaInfServices(RequestProcessorBuilder.class)
   @Name("newSequence")
   public static class Builder implements RequestProcessorBuilder, InitFromParam<Builder> {
      private String sequence;
      private Session.ConcurrencyPolicy policy = Session.ConcurrencyPolicy.FAIL;

      /**
       * @param param Sequence name.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return sequence(param);
      }

      /**
       * Maximum number of sequences instantiated.
       *
       * @param maxSequences Number of sequences.
       * @return Self.
       */
      @Deprecated
      public Builder maxSequences(int maxSequences) {
         log.warn("Property nextSequence.maxSequences is deprecated. Use concurrency setting in target sequence instead.");
         return this;
      }

      /**
       * Variable storing the counter for sequence IDs.
       *
       * @param counterVar Variable name.
       * @return Self.
       */
      @Deprecated
      public Builder counterVar(String counterVar) {
         log.warn("Property nextSequence.maxSequences is deprecated. Counters are held internally.");
         return this;
      }

      /**
       * Name of the instantiated sequence.
       *
       * @param sequence Sequence name.
       * @return Self.
       */
      public Builder sequence(String sequence) {
         this.sequence = sequence;
         return this;
      }

      /**
       * What should we do when the sequence concurrency factor is exceeded.
       *
       * @param policy The behaviour.
       * @return Self.
       */
      public Builder concurrencyPolicy(Session.ConcurrencyPolicy policy) {
         this.policy = policy;
         return this;
      }

      @Override
      public NewSequenceProcessor build(boolean fragmented) {
         if (sequence == null) {
            throw new BenchmarkDefinitionException("Undefined sequence template");
         }
         return new NewSequenceProcessor(sequence, policy);
      }
   }
}

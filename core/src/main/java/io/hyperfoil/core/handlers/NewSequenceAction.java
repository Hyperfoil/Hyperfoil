package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class NewSequenceAction implements Action {
   private static final Logger log = LogManager.getLogger(NewSequenceAction.class);

   private final String sequence;
   private final boolean forceSameIndex;
   private final Session.ConcurrencyPolicy policy;

   public NewSequenceAction(String sequence, boolean forceSameIndex, Session.ConcurrencyPolicy policy) {
      this.sequence = sequence;
      this.forceSameIndex = forceSameIndex;
      this.policy = policy;
   }

   @Override
   public void run(Session session) {
      session.startSequence(sequence, forceSameIndex, policy);
   }

   /**
    * Instantiates a sequence for each invocation.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("newSequence")
   public static class Builder implements Action.Builder, InitFromParam<Builder> {
      private String sequence;
      private boolean forceSameIndex;
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
      @SuppressWarnings("unused")
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
      @SuppressWarnings("unused")
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
       * Forces that the sequence will have the same index as the currently executing sequence.
       * This can be useful if the sequence is passing some data to the new sequence using sequence-scoped variables.
       * Note that the new sequence must have same concurrency factor as the currently executing sequence.
       *
       * @param force True if the index is forced, false otherwise (default is false).
       * @return Self.
       */
      public Builder forceSameIndex(boolean force) {
         this.forceSameIndex = force;
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
      public NewSequenceAction build() {
         if (sequence == null) {
            throw new BenchmarkDefinitionException("Undefined sequence template");
         }
         return new NewSequenceAction(sequence, forceSameIndex, policy);
      }
   }
}

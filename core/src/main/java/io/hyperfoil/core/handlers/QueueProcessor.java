package io.hyperfoil.core.handlers;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.data.Queue;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;

public class QueueProcessor implements Processor, ResourceUtilizer {
   private final ObjectAccess var;
   private final int maxSize;
   private final DataFormat format;
   private final String sequence;
   private final int concurrency;
   private final Action onCompletion;
   private final Session.ResourceKey<Queue> key;

   public QueueProcessor(Session.ResourceKey<Queue> key, ObjectAccess var, int maxSize, DataFormat format, String sequence,
         int concurrency, Action onCompletion) {
      this.key = key;
      this.var = var;
      this.maxSize = maxSize;
      this.format = format;
      this.sequence = sequence;
      this.concurrency = concurrency;
      this.onCompletion = onCompletion;
   }

   @Override
   public void before(Session session) {
      Queue queue = session.getResource(key);
      queue.reset(session);
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      ensureDefragmented(isLastPart);
      Queue queue = session.getResource(key);
      Object value = format.convert(data, offset, length);
      queue.push(session, value);
   }

   @Override
   public void after(Session session) {
      Queue queue = session.getResource(key);
      queue.producerComplete(session);
   }

   @Override
   public void reserve(Session session) {
      // If there are multiple concurrent requests all the data end up in single queue;
      // there's no way to set up different output var so merging them is the only useful behaviour.
      if (!var.isSet(session)) {
         var.setObject(session, ObjectVar.newArray(session, concurrency));
      }
      session.declareResource(key, () -> new Queue(var, maxSize, concurrency, sequence, onCompletion), true);
   }

   /**
    * Stores defragmented data in a queue. <br>
    * For each item in the queue a new sequence instance will be started
    * (subject the concurrency constraints) with sequence index that allows it to read an object from an array
    * using sequence-scoped access.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("queue")
   public static class Builder implements Processor.Builder {
      private String var;
      private int maxSize;
      private DataFormat format = DataFormat.STRING;
      private int concurrency;
      private String sequence;
      private Action.Builder onCompletion;
      private ObjectAccess varAccess;
      private Queue.Key key;
      private SequenceBuilder sequenceBuilder;
      private Consumer<Action.Builder> sequenceCompletion;

      /**
       * Variable storing the array that it used as a output object from the queue.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder var(String var) {
         this.var = var;
         return this;
      }

      /**
       * Maximum number of elements that can be stored in the queue.
       *
       * @param maxSize Number of objects.
       * @return Self.
       */
      public Builder maxSize(int maxSize) {
         this.maxSize = maxSize;
         return this;
      }

      /**
       * Conversion format from byte buffers. Default format is STRING.
       *
       * @param format Data format.
       * @return Self.
       */
      public Builder format(DataFormat format) {
         this.format = format;
         return this;
      }

      /**
       * Maximum number of started sequences that can be running at one moment.
       *
       * @param concurrency Number of sequences.
       * @return Self.
       */
      public Builder concurrency(int concurrency) {
         this.concurrency = concurrency;
         return this;
      }

      /**
       * Name of the started sequence.
       *
       * @param sequence Name.
       * @return Self.
       */
      public Builder sequence(String sequence) {
         this.sequence = sequence;
         return this;
      }

      public Builder sequence(SequenceBuilder sequenceBuilder, Consumer<Action.Builder> sequenceCompletion) {
         this.sequenceBuilder = sequenceBuilder;
         this.sequenceCompletion = sequenceCompletion;
         return this;
      }

      /**
       * Custom action that should be performed when the last consuming sequence reports that it has processed
       * the last element from the queue. Note that the sequence is NOT automatically augmented to report completion.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Action.Builder> onCompletion() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, this::onCompletion);
      }

      public Builder onCompletion(Action.Builder onCompletion) {
         this.onCompletion = onCompletion;
         return this;
      }

      @Override
      public void prepareBuild() {
         if (var == null) {
            throw new BenchmarkDefinitionException("Missing 'var' to store the queue.");
         }
         varAccess = SessionFactory.objectAccess(var);
         key = new Queue.Key();

         Locator locator = Locator.current();
         if (sequence != null && sequenceBuilder != null) {
            throw new BenchmarkDefinitionException("Cannot set sequence using both name and builder.");
         } else if (sequence == null && sequenceBuilder == null) {
            throw new BenchmarkDefinitionException("No sequence was set!");
         }
         if (sequenceBuilder == null) {
            SequenceBuilder originalSequence = locator.scenario().findSequence(sequence);
            String generatedSeqName = String.format("%s_queue_%08x", this.sequence, ThreadLocalRandom.current().nextInt());
            sequenceBuilder = locator.scenario().sequence(generatedSeqName, originalSequence);
         }
         Queue.Key myKey = key; // prevent capturing self reference
         if (sequenceCompletion == null) {
            sequenceBuilder.step(s -> {
               s.getResource(myKey).consumed(s);
               return true;
            });
         } else {
            sequenceCompletion.accept(() -> s -> s.getResource(myKey).consumed(s));
         }
         sequenceBuilder.concurrency(concurrency);
         // We must invoke the prepareBuild() in copied sequences manually
         sequenceBuilder.prepareBuild();
      }

      @Override
      public Processor build(boolean fragmented) {
         if (maxSize <= 0) {
            throw new BenchmarkDefinitionException("Maximum size for queue to " + var + " must be set!");
         }
         Action completionAction = onCompletion == null ? null : onCompletion.build();
         QueueProcessor processor = new QueueProcessor(key, varAccess, maxSize, format, sequenceBuilder.name(), concurrency,
               completionAction);
         return fragmented ? new DefragProcessor(processor) : processor;
      }
   }
}

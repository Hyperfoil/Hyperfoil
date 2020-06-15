package io.hyperfoil.core.handlers;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.RequestProcessorBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.data.Queue;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;

public class QueueProcessor implements Processor, ResourceUtilizer {
   private final Access var;
   private final int maxSize;
   private final DataFormat format;
   private final String sequence;
   private final int concurrency;
   private final Action onCompletion;
   private final Session.ResourceKey<Queue> key;

   public QueueProcessor(Session.ResourceKey<Queue> key, Access var, int maxSize, DataFormat format, String sequence, int concurrency, Action onCompletion) {
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
      queue.reset();
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
      var.declareObject(session);
      // If there are multiple concurrent requests all the data end up in single queue;
      // there's no way to set up different output var so merging them is the only useful behaviour.
      if (!var.isSet(session)) {
         var.setObject(session, ObjectVar.newArray(session, concurrency));
      }
      session.declareResource(key, () -> new Queue(var, maxSize, concurrency, sequence, onCompletion), true);
      ResourceUtilizer.reserve(session, onCompletion);
   }

   @MetaInfServices(RequestProcessorBuilder.class)
   @Name("queue")
   public static class Builder implements RequestProcessorBuilder {
      private String var;
      private int maxSize;
      private DataFormat format = DataFormat.STRING;
      private int concurrency;
      private String sequence;
      private Action.Builder onCompletion;
      private Access varAccess;
      private Session.ResourceKey<Queue> key;
      private SequenceBuilder sequenceBuilder;
      private Consumer<Action.Builder> sequenceCompletion;

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      public Builder maxSize(int maxSize) {
         this.maxSize = maxSize;
         return this;
      }

      public Builder format(DataFormat format) {
         this.format = format;
         return this;
      }

      public Builder concurrency(int concurrency) {
         this.concurrency = concurrency;
         return this;
      }

      public Builder sequence(String sequence) {
         this.sequence = sequence;
         return this;
      }

      public Builder sequence(SequenceBuilder sequenceBuilder, Consumer<Action.Builder> sequenceCompletion) {
         this.sequenceBuilder = sequenceBuilder;
         this.sequenceCompletion = sequenceCompletion;
         return this;
      }

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
         varAccess = SessionFactory.access(var);
         key = new Session.ResourceKey<Queue>() {};

         Locator locator = Locator.current();
         if (sequence != null && sequenceBuilder != null) {
            throw new BenchmarkDefinitionException("Cannot set sequence using both name and builder.");
         } else if (sequence == null && sequenceBuilder == null) {
            throw new BenchmarkDefinitionException("No sequence was set!");
         }
         if (sequenceBuilder == null) {
            SequenceBuilder originalSequence = locator.scenario().findSequence(sequence);
            String generatedSeqName = String.format("%s_queue_%08x", this.sequence, ThreadLocalRandom.current().nextInt());
            sequenceBuilder = locator.scenario().sequence(generatedSeqName);
            sequenceBuilder.readFrom(originalSequence);
         }
         if (sequenceCompletion == null) {
            sequenceBuilder.step(s -> {
               s.getResource(key).consumed(s);
               return true;
            });
         } else {
            sequenceCompletion.accept(() -> s -> s.getResource(key).consumed(s));
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
         QueueProcessor processor = new QueueProcessor(key, varAccess, maxSize, format, sequenceBuilder.name(), concurrency, onCompletion.build());
         return fragmented ? new DefragProcessor(processor) : processor;
      }
   }
}

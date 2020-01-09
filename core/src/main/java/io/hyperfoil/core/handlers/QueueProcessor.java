package io.hyperfoil.core.handlers;

import java.util.concurrent.ThreadLocalRandom;

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
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;

public class QueueProcessor implements Processor, ResourceUtilizer {
   private final Access var;
   private final int maxSize;
   private final DataFormat format;
   private final String sequence;
   private final int concurrency;
   private final Action onCompletion;

   public QueueProcessor(Access var, int maxSize, DataFormat format, String sequence, int concurrency, Action onCompletion) {
      this.var = var;
      this.maxSize = maxSize;
      this.format = format;
      this.sequence = sequence;
      this.concurrency = concurrency;
      this.onCompletion = onCompletion;
   }

   private Queue queue(Session session) {
      return (Queue) var.getObject(session);
   }

   @Override
   public void before(Session session) {
      Queue queue = queue(session);
      queue.reset();
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      ensureDefragmented(isLastPart);
      Queue queue = queue(session);
      Object value = format.convert(data, offset, length);
      queue.push(session, value);
   }

   @Override
   public void after(Session session) {
      Queue queue = queue(session);
      queue.producerComplete(session);
   }

   @Override
   public void reserve(Session session) {
      var.declareObject(session);
      if (var.isSet(session)) {
         throw new BenchmarkDefinitionException("Queue is already defined in " + var);
      }
      var.setObject(session, new Queue(session, var.toString(), maxSize, concurrency, sequence, onCompletion));
      ResourceUtilizer.reserve(session, onCompletion);
   }

   @MetaInfServices(RequestProcessorBuilder.class)
   @Name("queue")
   public static class Builder implements RequestProcessorBuilder {
      private Locator locator;
      private String var;
      private int maxSize;
      private DataFormat format = DataFormat.STRING;
      private int concurrency;
      private String sequence;
      private Action.Builder onCompletion;
      private String generatedSeqName;
      private Access varAccess;

      @Override
      public Builder setLocator(Locator locator) {
         this.locator = locator;
         return this;
      }

      @Override
      public Builder copy(Locator locator) {
         return new Builder().setLocator(locator)
               .var(var).maxSize(maxSize).format(format).concurrency(concurrency)
               .sequence(sequence).onCompletion(onCompletion.copy(locator));
      }

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

      public ServiceLoadedBuilderProvider<Action.Builder> onCompletion() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, locator, this::onCompletion);
      }

      private Builder onCompletion(Action.Builder onCompletion) {
         this.onCompletion = onCompletion;
         return this;
      }

      @Override
      public void prepareBuild() {
         if (var == null) {
            throw new BenchmarkDefinitionException("Missing 'var' to store the queue.");
         }
         varAccess = SessionFactory.access(var);

         SequenceBuilder originalSequence = locator.scenario().findSequence(this.sequence);
         generatedSeqName = String.format("%s_queue_%08x", this.sequence, ThreadLocalRandom.current().nextInt());
         SequenceBuilder newSequence = locator.scenario().sequence(generatedSeqName);
         newSequence.readFrom(originalSequence);
         newSequence.step(s -> {
            Queue queue = (Queue) varAccess.getObject(s);
            queue.consumed(s, s.currentSequence().index());
            return true;
         });
         // We must invoke the prepareBuild() in copied sequences manually
         newSequence.prepareBuild();
      }

      @Override
      public Processor build(boolean fragmented) {
         QueueProcessor processor = new QueueProcessor(varAccess, maxSize, format, generatedSeqName, concurrency, onCompletion.build());
         return fragmented ? new DefragProcessor(processor) : processor;
      }
   }
}

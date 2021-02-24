package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.netty.buffer.ByteBuf;

public class MultiProcessor implements Processor, ResourceUtilizer {
   protected final Processor[] delegates;

   @SafeVarargs
   public MultiProcessor(Processor... delegates) {
      this.delegates = delegates;
   }

   @Override
   public void before(Session session) {
      for (Processor p : delegates) {
         p.before(session);
      }
   }

   @Override
   public void after(Session session) {
      for (Processor p : delegates) {
         p.after(session);
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      for (Processor p : delegates) {
         p.process(session, data, offset, length, isLastPart);
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) delegates);
   }

   public static class Builder<S extends Builder<S>> implements Processor.Builder {
      public final List<Processor.Builder> delegates = new ArrayList<>();

      @SuppressWarnings("unchecked")
      @Override
      public Processor build(boolean fragmented) {
         Processor[] delegates = buildProcessors(fragmented);
         return new MultiProcessor(delegates);
      }

      protected Processor[] buildProcessors(boolean fragmented) {
         Processor[] delegates = this.delegates.stream().map(d -> d.build(fragmented)).toArray(Processor[]::new);
         return delegates;
      }

      public Processor buildSingle(boolean fragmented) {
         if (delegates.size() == 1) {
            return delegates.get(0).build(fragmented);
         } else {
            return new MultiProcessor(buildProcessors(fragmented));
         }
      }

      public S processor(Processor.Builder processor) {
         delegates.add(processor);
         return self();
      }

      public S processors(Collection<? extends Processor.Builder> processors) {
         delegates.addAll(processors);
         return self();
      }

      /**
       * Add one or more delegated processors.
       *
       * @return Builder;
       */
      public ServiceLoadedBuilderProvider<Processor.Builder> processor() {
         return new ServiceLoadedBuilderProvider<>(Processor.Builder.class, this::processor);
      }

      @SuppressWarnings("unchecked")
      protected S self() {
         return (S) this;
      }

      public boolean isEmpty() {
         return delegates.isEmpty();
      }
   }
}

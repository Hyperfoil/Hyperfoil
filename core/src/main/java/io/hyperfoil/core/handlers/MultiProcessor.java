package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.netty.buffer.ByteBuf;

public class MultiProcessor implements Processor {
   protected final Processor[] delegates;

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

   public static class Builder<P, S extends Builder<P, S>> implements Processor.Builder {
      public final P parent;
      public final List<Processor.Builder> delegates = new ArrayList<>();

      public Builder(P parent) {
         this.parent = parent;
      }

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
       * Add one or more processors.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Processor.Builder> processor() {
         return new ServiceLoadedBuilderProvider<>(Processor.Builder.class, this::processor);
      }

      @SuppressWarnings("unchecked")
      protected S self() {
         return (S) this;
      }

      public P end() {
         return parent;
      }

      public boolean isEmpty() {
         return delegates.isEmpty();
      }
   }
}

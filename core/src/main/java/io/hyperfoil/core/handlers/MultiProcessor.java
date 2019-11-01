package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.List;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public final class MultiProcessor<R extends Request> implements Processor<R>, ResourceUtilizer {
   protected final Processor<R>[] delegates;

   @SafeVarargs
   public MultiProcessor(Processor<R>... delegates) {
      this.delegates = delegates;
   }

   @Override
   public void before(R request) {
      for (Processor<R> p : delegates) {
         p.before(request);
      }
   }

   @Override
   public void after(R request) {
      for (Processor<R> p : delegates) {
         p.after(request);
      }
   }

   @Override
   public void process(R request, ByteBuf data, int offset, int length, boolean isLastPart) {
      for (Processor<R> p : delegates) {
         p.process(request, data, offset, length, isLastPart);
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) delegates);
   }

   public static class Builder<R extends Request> implements Processor.Builder<R, Builder<R>> {
      public final List<Processor.Builder<R, ?>> delegates = new ArrayList<>();

      @SuppressWarnings("unchecked")
      @Override
      public Processor<R> build() {
         Processor[] delegates = this.delegates.stream().map(Processor.Builder::build).toArray(Processor[]::new);
         return new MultiProcessor<R>(delegates);
      }

      @Override
      public void prepareBuild() {
         delegates.forEach(Processor.Builder::prepareBuild);
      }

      @Override
      public Builder<R> copy(Locator locator) {
         Builder<R> builder = new Builder<>();
         delegates.forEach(b -> builder.delegates.add(b.copy(locator)));
         return builder;
      }

      public Builder<R> add(Processor.Builder<R, ?> processor) {
         delegates.add(processor);
         return this;
      }
   }
}

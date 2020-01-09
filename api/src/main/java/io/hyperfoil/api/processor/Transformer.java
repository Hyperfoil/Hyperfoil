package io.hyperfoil.api.processor;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public interface Transformer extends Serializable {
   default void before(Session session) {}

   void transform(Session session, ByteBuf in, int offset, int length, boolean lastFragment, ByteBuf out);

   default void after(Session session) {}

   interface Builder extends BuilderBase<Builder> {
      Transformer build(boolean fragmented);
   }

   class ProcessorAdapter implements Transformer, ResourceUtilizer {
      private final Processor delegate;

      public ProcessorAdapter(Processor delegate) {
         this.delegate = delegate;
      }

      @Override
      public void before(Session session) {
         delegate.before(session);
      }

      @Override
      public void transform(Session session, ByteBuf input, int offset, int length, boolean isLastFragment, ByteBuf output) {
         delegate.process(session, input, offset, length, isLastFragment);
      }

      @Override
      public void after(Session session) {
         delegate.after(session);
      }

      @Override
      public void reserve(Session session) {
         ResourceUtilizer.reserve(session, delegate);
      }
   }
}

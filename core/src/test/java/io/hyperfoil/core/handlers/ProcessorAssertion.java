package io.hyperfoil.core.handlers;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.vertx.ext.unit.TestContext;

public class ProcessorAssertion {
   private final int assertInvocations;
   private final boolean onlyLast;
   private int actualInvocations;

   public ProcessorAssertion(int assertInvocations, boolean onlyLast) {
      this.assertInvocations = assertInvocations;
      this.onlyLast = onlyLast;
   }

   public Processor.Builder processor(Processor.Builder delegate) {
      return new Builder(delegate);
   }

   public void runAssertions(TestContext ctx) {
      ctx.assertEquals(assertInvocations, actualInvocations);
      actualInvocations = 0;
   }

   private class Builder implements Processor.Builder {
      private final Processor.Builder delegate;

      private Builder(Processor.Builder delegate) {
         this.delegate = delegate;
      }

      @Override
      public Processor build(boolean fragmented) {
         return new Instance(delegate.build(fragmented));
      }
   }

   private class Instance extends Processor.BaseDelegating {
      protected Instance(Processor delegate) {
         super(delegate);
      }

      @Override
      public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
         if (isLastPart || !onlyLast) {
            actualInvocations++;
         }
         delegate.process(session, data, offset, length, isLastPart);
      }
   }
}

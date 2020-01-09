package io.hyperfoil.api.processor;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public interface Processor extends Serializable {
   /**
    * Invoked before we record first value from given response.
    *
    * @param session Request.
    */
   default void before(Session session) {
   }

   void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart);

   /**
    * Invoked after we record the last value from given response.
    *
    * @param session Request.
    */
   default void after(Session session) {
   }

   default void ensureDefragmented(boolean isLastPart) {
      if (!isLastPart) {
         throw new IllegalStateException("This processor expects defragmented data.");
      }
   }

   interface Builder<B extends Builder<B>> extends BuilderBase<B> {
      Processor build(boolean fragmented);
   }

   abstract class BaseDelegating implements Processor, ResourceUtilizer {
      protected final Processor delegate;

      protected BaseDelegating(Processor delegate) {
         this.delegate = delegate;
      }

      @Override
      public void before(Session session) {
         delegate.before(session);
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

   class ActionAdapter implements Processor, ResourceUtilizer {
      private final Action action;

      public ActionAdapter(Action action) {
         this.action = action;
      }

      @Override
      public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
         // Action should be performed only when the last chunk arrives
         if (!isLastPart) {
            return;
         }
         action.run(session);
      }

      @Override
      public void reserve(Session session) {
         ResourceUtilizer.reserve(session, action);
      }
   }
}

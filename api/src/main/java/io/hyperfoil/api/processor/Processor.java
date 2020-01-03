package io.hyperfoil.api.processor;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public interface Processor<R extends Request> extends Serializable {
   /**
    * Invoked before we record first value from given response.
    *
    * @param request Request.
    */
   default void before(R request) {
   }

   void process(R request, ByteBuf data, int offset, int length, boolean isLastPart);

   /**
    * Invoked after we record the last value from given response.
    *
    * @param request Request.
    */
   default void after(R request) {
   }

   default void ensureDefragmented(boolean isLastPart) {
      if (!isLastPart) {
         throw new IllegalStateException("This processor expects defragmented data.");
      }
   }

   interface Builder<R extends Request, B extends Builder<R, B>> extends BuilderBase<B> {
      Processor<R> build(boolean fragmented);
   }

   abstract class BaseDelegating<R extends Request> implements Processor<R>, ResourceUtilizer {
      protected final Processor<R> delegate;

      protected BaseDelegating(Processor<R> delegate) {
         this.delegate = delegate;
      }

      @Override
      public void before(R request) {
         delegate.before(request);
      }

      @Override
      public void after(R request) {
         delegate.after(request);
      }

      @Override
      public void reserve(Session session) {
         ResourceUtilizer.reserve(session, delegate);
      }
   }

   class ActionAdapter<R extends Request> implements Processor<R>, ResourceUtilizer {
      private final Action action;

      public ActionAdapter(Action action) {
         this.action = action;
      }

      @Override
      public void process(R request, ByteBuf data, int offset, int length, boolean isLastPart) {
         // Action should be performed only when the last chunk arrives
         if (!isLastPart) {
            return;
         }
         action.run(request.session);
      }

      @Override
      public void reserve(Session session) {
         ResourceUtilizer.reserve(session, action);
      }
   }
}

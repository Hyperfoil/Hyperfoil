package io.hyperfoil.api.connection;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public interface Processor<R extends Request> extends Serializable {
   /**
    * Invoked before we record first value from given response.
    * @param request Request.
    */
   default void before(R request) {
   }

   void process(R request, ByteBuf data, int offset, int length, boolean isLastPart);

   /**
    * Invoked after we record the last value from given response.
    * @param request Request.
    */
   default void after(R request) {
   }

   interface Builder<R extends Request> extends BuilderBase<Builder<R>> {
      Processor<R> build();
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
         if (delegate instanceof ResourceUtilizer) {
            ((ResourceUtilizer) delegate).reserve(session);
         }
      }
   }
}

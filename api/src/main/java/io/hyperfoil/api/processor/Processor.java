package io.hyperfoil.api.processor;

import java.io.Serializable;
import java.util.function.Function;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.IncludeBuilders;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public interface Processor extends Serializable {
   static Processor.Builder adapt(Action.Builder builder) {
      return new ActionBuilderAdapter(builder);
   }

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

   @IncludeBuilders(
         @IncludeBuilders.Conversion(from = Action.Builder.class, adapter = Processor.ActionBuilderConverter.class)
   )
   interface Builder extends BuilderBase<Builder> {
      Processor build(boolean fragmented);
   }

   abstract class BaseDelegating implements Processor {
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
   }

   class ActionAdapter implements Processor {
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
   }

   class ActionBuilderConverter implements Function<Action.Builder, Processor.Builder> {
      @Override
      public Processor.Builder apply(Action.Builder builder) {
         return new ActionBuilderAdapter(builder);
      }
   }

   class ActionBuilderAdapter implements Processor.Builder {
      private final Action.Builder builder;

      public ActionBuilderAdapter(Action.Builder builder) {
         this.builder = builder;
      }

      @Override
      public Processor.Builder copy(Object newParent) {
         return new ActionBuilderAdapter(builder.copy(null));
      }

      @Override
      public Processor build(boolean fragmented) {
         return new ActionAdapter(builder.build());
      }
   }
}

package io.sailrocket.core.extractors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Predicate;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.session.Session;

public class ExpectProcessor implements Session.Processor {
   int beforeCalled;
   int afterCalled;
   int invoked;
   final Deque<Invocation> invocations = new LinkedList<>();

   @Override
   public void before(Session session) {
      assertThat(beforeCalled).isEqualTo(0);
      beforeCalled++;
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      Invocation invocation = invocations.pollFirst();
      assertThat(invocation).isNotNull();
      if (invocation.data != null) assertThat(data).matches(invocation.data);
      assertThat(offset).as("Invocation #%d", invoked).isEqualTo(invocation.offset);
      assertThat(length).as("Invocation #%d", invoked).isEqualTo(invocation.length);
      assertThat(isLastPart).as("Invocation #%d", invoked).isEqualTo(invocation.isLastPart);
      ++invoked;
   }

   @Override
   public void after(Session session) {
      assertThat(afterCalled).isEqualTo(0);
      afterCalled++;
   }

   public ExpectProcessor expect(int offset, int length, boolean isLastPart) {
      invocations.add(new Invocation(null, offset, length, isLastPart));
      return this;
   }

   public ExpectProcessor expect(ByteBuf data, int offset, int length, boolean isLastPart) {
      invocations.add(new Invocation(data::equals, offset, length, isLastPart));
      return this;
   }

   public void validate() {
      assertThat(beforeCalled).isEqualTo(1);
      assertThat(invocations).isEmpty();
      assertThat(afterCalled).isEqualTo(1);
   }

   private class Invocation {
      final Predicate<ByteBuf> data;
      final int offset;
      final int length;
      final boolean isLastPart;

      private Invocation(Predicate<ByteBuf> data, int offset, int length, boolean isLastPart) {
         this.data = data;
         this.offset = offset;
         this.length = length;
         this.isLastPart = isLastPart;
      }
   }
}

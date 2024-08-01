package io.hyperfoil.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.test.CustomExecutorRunner;
import io.netty.channel.DefaultEventLoop;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;

@ExtendWith(CustomExecutorRunner.class)
public class EventExecutorSessionPoolTest extends PoolTest<Session> {

   private static EventExecutor[] executors;
   private int nextEventExecutor = 0;

   @BeforeAll
   public static void configureRunnerExecutor() {
      final var eventExecutors = new DefaultEventExecutorGroup(11);
      executors = StreamSupport.stream(eventExecutors.spliterator(), false)
            .map(EventExecutor.class::cast).toArray(EventExecutor[]::new);
      CustomExecutorRunner.TEST_EVENT_EXECUTOR = eventExecutors;
   }

   @Override
   protected ElasticPool<Session> createPoolWith(final Supplier<Session> initSupplier) {
      return new AffinityAwareSessionPool(executors, initSupplier);
   }

   @Override
   protected Session createNewItem() {
      final Session session = mock(Session.class);
      final var eventExecutor = executors[nextEventExecutor];
      when(session.executor()).thenReturn(eventExecutor);
      when(session.agentThreadId()).thenReturn(nextEventExecutor);
      nextEventExecutor++;
      if (nextEventExecutor >= executors.length) {
         nextEventExecutor = 0;
      }
      return session;
   }

   @Test
   public void reserveBeyondReservedCapacityReuseAndExtendTheCapacity() {
      final int reservedCapacity = 10;
      final var reservedItemsCounter = new AtomicInteger();
      final var pool = createPoolWith(() -> {
         reservedItemsCounter.incrementAndGet();
         return createNewItem();
      });
      assertEquals(0, reservedItemsCounter.get());
      pool.reserve(reservedCapacity);
      final var reservedItems = new HashSet<Session>(reservedCapacity);
      for (int i = 0; i < reservedCapacity; i++) {
         final var acquired = pool.acquire();
         assertTrue(reservedItems.add(acquired));
         assertNotNull(acquired);
      }
      // release all reserved items
      for (final Session acquired : reservedItems) {
         pool.release(acquired);
      }
      assertEquals(reservedCapacity, reservedItemsCounter.get());
      final int additionalCapacity = 10;
      pool.reserve(reservedCapacity + additionalCapacity);
      assertEquals(reservedCapacity + additionalCapacity, reservedItemsCounter.get());
      final var newReservedItems = new HashSet<Session>(reservedCapacity + additionalCapacity);
      for (int i = 0; i < reservedCapacity + additionalCapacity; i++) {
         final var acquired = pool.acquire();
         assertNotNull(acquired);
         assertTrue(newReservedItems.add(acquired));
      }
      assertTrue(newReservedItems.containsAll(reservedItems));
      assertNull(pool.acquire());
   }

   @Test
   public void acquireReleaseWithinReservedCapacityFromAlienThread() {
      var error = new CompletableFuture<>();
      Thread alienThread = new Thread(() -> {
         try {
            super.acquireReleaseWithinReservedCapacity();
            error.complete(null);
         } catch (Throwable t) {
            error.completeExceptionally(t);
         }
      });
      alienThread.start();
      assertNull(error.join());
   }

   @Test
   public void acquireReleaseWithinReservedCapacityFromAlienEventExecutor() {
      var eventLoop = new DefaultEventLoop();
      try {
         var testResult = eventLoop.submit(super::acquireReleaseWithinReservedCapacity);
         // capture the exception if any
         try {
            testResult.get();
         } catch (Throwable e) {
            fail(e.getMessage());
         }
      } finally {
         eventLoop.shutdownGracefully();
      }
   }
}

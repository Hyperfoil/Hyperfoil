package io.hyperfoil.core.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import io.hyperfoil.api.collection.ElasticPool;

public abstract class PoolTest<T> {

   protected abstract ElasticPool<T> createPoolWith(Supplier<T> initSupplier);

   /**
    * The pooled items factory must create a whole new instance each time, to work
    */
   protected abstract T createNewItem();

   @Test
   public void acquireReleaseWithinReservedCapacity() {
      final int reservedCapacity = 10;
      final var reservedItemsCounter = new AtomicInteger();
      final var pool = createPoolWith(() -> {
         reservedItemsCounter.incrementAndGet();
         return createNewItem();
      });
      assertEquals(0, reservedItemsCounter.get());
      pool.reserve(reservedCapacity);

      final var reservedItems = new HashSet<T>(reservedCapacity);
      for (int i = 0; i < reservedCapacity; i++) {
         final var acquired = pool.acquire();
         assertNotNull(acquired);
         assertTrue(reservedItems.add(acquired));
         assertEquals(reservedCapacity, reservedItemsCounter.get());
         assertEquals(i + 1, pool.maxUsed());
         assertEquals(0, pool.minUsed());
      }
      assertEquals(reservedCapacity, reservedItems.size());
      for (final T acquired : reservedItems) {
         pool.release(acquired);
         assertEquals(reservedCapacity, reservedItemsCounter.get());
         assertEquals(reservedCapacity, pool.maxUsed());
         assertEquals(0, pool.minUsed());
      }
      final var reservedItemsReacquired = new HashSet<T>(reservedCapacity);
      for (int i = 0; i < reservedCapacity; i++) {
         final var acquired = pool.acquire();
         assertNotNull(acquired);
         assertTrue(reservedItemsReacquired.add(acquired));
         assertEquals(reservedCapacity, reservedItemsCounter.get());
         assertEquals(reservedCapacity, pool.maxUsed());
         assertEquals(0, pool.minUsed());
      }
      assertEquals(reservedItems, reservedItemsReacquired);
   }

   @Test(expected = Exception.class)
   public void cannotAcquireAcquireWithoutReservingFirst() {
      final var pool = createPoolWith(() -> {
         Assert.fail("Init supplier should not be called");
         return null;
      });
      pool.acquire();
   }

   @Test(expected = Exception.class)
   public void cannotReleaseWithoutReservingFirst() {
      final var pool = createPoolWith(() -> {
         Assert.fail("Init supplier should not be called");
         return null;
      });
      pool.release(createNewItem());
   }

   @Test(expected = Exception.class)
   public void cannotReleaseANullItem() {
      final var pool = createPoolWith(this::createNewItem);
      pool.reserve(1);
      // ignore what's acquired
      assertNotNull(pool.acquire());
      pool.release(null);
   }

   @Test
   public void reserveBelowExistingReservedCapacityShouldReuseWholeCapacity() {
      final int reservedCapacity = 10;
      final var reservedItemsCounter = new AtomicInteger();
      final var pool = createPoolWith(() -> {
         reservedItemsCounter.incrementAndGet();
         return createNewItem();
      });
      assertEquals(0, reservedItemsCounter.get());
      pool.reserve(reservedCapacity);
      assertEquals(reservedCapacity, reservedItemsCounter.get());
      pool.reserve(0);
      for (int i = 0; i < reservedCapacity; i++) {
         assertNotNull(pool.acquire());
      }
   }

   @Test
   public void statsShouldReflectPoolUsage() {
      final int reservedCapacity = 10;
      final var pool = createPoolWith(this::createNewItem);
      pool.reserve(reservedCapacity);
      var reservedItems = new ArrayDeque<T>(reservedCapacity);
      for (int i = 0; i < reservedCapacity; i++) {
         reservedItems.add(pool.acquire());
         assertEquals(0, pool.minUsed());
         assertEquals(i + 1, pool.maxUsed());
      }
      pool.resetStats();
      assertEquals(reservedCapacity, pool.maxUsed());
      assertEquals(reservedCapacity, pool.minUsed());
      for (int i = 0; i < reservedCapacity; i++) {
         var toRelease = reservedItems.poll();
         pool.release(toRelease);
         assertEquals(reservedCapacity - (i + 1), pool.minUsed());
         assertEquals(reservedCapacity, pool.maxUsed());
      }
   }

}

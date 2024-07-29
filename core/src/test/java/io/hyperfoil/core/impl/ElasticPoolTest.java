package io.hyperfoil.core.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import io.hyperfoil.api.collection.ElasticPool;

public abstract class ElasticPoolTest<T> extends PoolTest<T> {

   protected abstract ElasticPool<T> createPoolWith(Supplier<T> initSupplier, Supplier<T> depletionSupplier);

   @Override
   protected ElasticPool<T> createPoolWith(final Supplier<T> initSupplier) {
      return createPoolWith(initSupplier, () -> {
         Assert.fail("Depleted supplier should not be called");
         return null;
      });
   }

   @Test
   public void acquireReleseBeyondReservedCapacity() {
      final int reservedCapacity = 10;
      final var reservedItemsCounter = new AtomicInteger();
      final var depletedItemsCounter = new AtomicInteger();
      final var pool = createPoolWith(() -> {
         reservedItemsCounter.incrementAndGet();
         return createNewItem();
      }, () -> {
         depletedItemsCounter.incrementAndGet();
         return createNewItem();
      });
      assertEquals(0, reservedItemsCounter.get());
      assertEquals(0, depletedItemsCounter.get());
      pool.reserve(reservedCapacity);

      final var acquiredItems = new HashSet<T>(reservedCapacity);
      for (int i = 0; i < reservedCapacity; i++) {
         final var acquired = pool.acquire();
         assertNotNull(acquired);
         assertTrue(acquiredItems.add(acquired));
         assertEquals(reservedCapacity, reservedItemsCounter.get());
         assertEquals(0, depletedItemsCounter.get());
         assertEquals(i + 1, pool.maxUsed());
         assertEquals(0, pool.minUsed());
      }
      final int depletedCapacity = 10;
      final var depletedItems = new HashSet<T>(depletedCapacity);
      for (int i = 0; i < depletedCapacity; i++) {
         final T depleted = pool.acquire();
         assertNotNull(depleted);
         assertFalse(acquiredItems.contains(depleted));
         assertTrue(depletedItems.add(depleted));
         assertEquals(reservedCapacity, reservedItemsCounter.get());
         assertEquals(i + 1, depletedItemsCounter.get());
         assertEquals(reservedCapacity + (i + 1), pool.maxUsed());
         assertEquals(0, pool.minUsed());
      }
      assertEquals(depletedCapacity, depletedItems.size());
      // release the depleted items
      for (final T depleted : depletedItems) {
         pool.release(depleted);
         assertEquals(reservedCapacity, reservedItemsCounter.get());
         assertEquals(depletedCapacity, depletedItemsCounter.get());
         assertEquals(reservedCapacity + depletedCapacity, pool.maxUsed());
         assertEquals(0, pool.minUsed());
      }
      // acquiring it again should just use the depleted items
      final var depletedItemsReacquired = new HashSet<T>(depletedCapacity);
      for (int i = 0; i < depletedCapacity; i++) {
         final var depleted = pool.acquire();
         assertNotNull(depleted);
         assertFalse(acquiredItems.contains(depleted));
         assertTrue(depletedItemsReacquired.add(depleted));
         assertEquals(reservedCapacity, reservedItemsCounter.get());
         assertEquals(depletedCapacity, depletedItemsCounter.get());
         assertEquals(reservedCapacity + depletedCapacity, pool.maxUsed());
         assertEquals(0, pool.minUsed());
      }
      // verify that the reacquired items are the same
      assertEquals(depletedItems, depletedItemsReacquired);
   }
}

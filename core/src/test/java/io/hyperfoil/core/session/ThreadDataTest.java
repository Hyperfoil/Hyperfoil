package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.session.ThreadData;

public class ThreadDataTest {
   private static final String FOO = "foo";
   private static final String NUMBER = "number";

   @Test
   public void testFlatData() {
      ThreadData data = new ThreadDataImpl();
      data.reserveMap(FOO, null, 3);
      for (int i = 0; i < 10; ++i) {
         ThreadData.SharedMap map = data.newMap(FOO);
         map.put(FOO, "bar" + i);
         map.put(NUMBER, i);
         data.pushMap(FOO, map);
      }
      int sum = 0;
      for (int i = 0; i < 10; ++i) {
         ThreadData.SharedMap map = data.pullMap(FOO);
         int number = (Integer) map.get(NUMBER);
         sum += number;
         data.releaseMap(FOO, map);
      }
      assertThat(sum).isEqualTo(45);
      assertThat(data.pullMap(FOO)).isNull();
   }

   @Test
   public void testIndexedData() {
      ThreadData data = new ThreadDataImpl();
      data.reserveMap(FOO, null, 5);
      data.reserveMap(FOO, FOO, 2);
      data.reserveMap(FOO, NUMBER, 3);

      int count = 0;
      for (int i = 0; i < 10; ++i) {
         for (int j = 0; j <= i; ++j) {
            ThreadData.SharedMap map = data.newMap(FOO);
            map.put(FOO, "bar" + i);
            map.put(NUMBER, j);
            data.pushMap(FOO, map);
            count++;
         }
      }
      for (int i = 0; i < count; ++i) {
         if (count % 5 == 0) {
            ThreadData.SharedMap map = data.pullMap(FOO);
            assertThat(map).isNotNull();
         } else {
            ThreadData.SharedMap map;
            int j;
            do {
               j = ThreadLocalRandom.current().nextInt(10);
               map = data.pullMap(FOO, NUMBER, j);
            } while (map == null);
            assertThat(map.get(NUMBER)).isEqualTo(j);
         }
      }
      assertThat(data.pullMap(FOO)).isNull();
   }
}

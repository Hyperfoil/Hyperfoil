package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

import io.hyperfoil.api.session.SharedData;

public class SharedDataTest {

   public static final String FOO = "foo";

   @Test
   public void testFlatData() {
      SharedData data = new SharedDataImpl();
      data.reserveMap(FOO, null, 3);
      for (int i = 0; i < 10; ++i) {
         SharedData.SharedMap map = data.newMap(FOO);
         map.put("foo", "bar" + i);
         map.put("number", i);
         data.pushMap(FOO, map);
      }
      int sum = 0;
      for (int i = 0; i < 10; ++i) {
         SharedData.SharedMap map = data.pullMap(FOO);
         int number = (Integer) map.find("number");
         sum += number;
         data.releaseMap(FOO, map);
      }
      assertThat(sum).isEqualTo(45);
      assertThat(data.pullMap(FOO)).isNull();
   }

   @Test
   public void testIndexedData() {
      SharedData data = new SharedDataImpl();
      data.reserveMap(FOO, null, 5);
      data.reserveMap(FOO, "foo", 2);
      data.reserveMap(FOO, "number", 3);

      int count = 0;
      for (int i = 0; i < 10; ++i) {
         for (int j = 0; j <=i; ++j) {
            SharedData.SharedMap map = data.newMap(FOO);
            map.put("foo", "bar" + i);
            map.put("number", j);
            data.pushMap(FOO, map);
            count++;
         }
      }
      for (int i = 0; i < count; ++i) {
         if (count % 5 == 0) {
            SharedData.SharedMap map = data.pullMap(FOO);
            assertThat(map).isNotNull();
         } else {
            SharedData.SharedMap map;
            int j;
            do {
               j = ThreadLocalRandom.current().nextInt(10);
               map = data.pullMap(FOO, "number", j);
            } while (map == null);
            assertThat(map.find("number")).isEqualTo(j);
         }
      }
      assertThat(data.pullMap(FOO)).isNull();
   }
}

package io.hyperfoil.core.extractors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class RangeStatusValidatorTest {
   RangeStatusValidator.BuilderFactory factory = new RangeStatusValidator.BuilderFactory();

   @Test
   public void test1() {
      RangeStatusValidator validator = create("200");
      assertThat(validator.min).isEqualTo(200);
      assertThat(validator.max).isEqualTo(200);
   }

   @Test
   public void test2() {
      RangeStatusValidator validator = create("3xx");
      assertThat(validator.min).isEqualTo(300);
      assertThat(validator.max).isEqualTo(399);
   }

   @Test
   public void test3() {
      RangeStatusValidator validator = create("201 - 205");
      assertThat(validator.min).isEqualTo(201);
      assertThat(validator.max).isEqualTo(205);
   }

   private RangeStatusValidator create(String inline) {
      return factory.newBuilder(null, inline).build();
   }
}

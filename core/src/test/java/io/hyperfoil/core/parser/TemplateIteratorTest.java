package io.hyperfoil.core.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.hyperfoil.impl.Util;

public class TemplateIteratorTest {
   @Test
   public void testTemplate() throws IOException {
      ClassLoader classLoader = this.getClass().getClassLoader();
      String template = Util.toString(classLoader.getResourceAsStream("template/template.yaml"));
      String expected = Util.toString(classLoader.getResourceAsStream("template/expected.yaml"));

      Yaml yaml = new Yaml();
      Iterator<Event> rawIterator = yaml.parse(new StringReader(template)).iterator();
      Map<String, String> params = Map.of("FOO", "foo", "BAR", "bar", "C", "c", "LIST_OF_ITEMS", "x;y");
      Iterator<Event> templateIterator = new TemplateIterator(rawIterator, params);
      Iterator<Event> expectedIterator = yaml.parse(new StringReader(expected)).iterator();

      while (templateIterator.hasNext() && expectedIterator.hasNext()) {
         Event fromExpected = expectedIterator.next();
         Event fromTemplate = templateIterator.next();

         assertThat(fromTemplate.getClass()).isEqualTo(fromExpected.getClass());
         if (fromTemplate instanceof ScalarEvent) {
            assertThat(((ScalarEvent) fromTemplate).getValue()).isEqualTo(((ScalarEvent) fromExpected).getValue());
            assertThat(((ScalarEvent) fromTemplate).getTag()).isNull();
         } else if (fromTemplate instanceof CollectionStartEvent) {
            assertThat(((CollectionStartEvent) fromTemplate).getTag()).isNull();
         }
         assertThat(fromTemplate).isNotExactlyInstanceOf(AliasEvent.class);
      }
      assertThat(templateIterator.hasNext()).isFalse();
      assertThat(expectedIterator.hasNext()).isFalse();
   }
}

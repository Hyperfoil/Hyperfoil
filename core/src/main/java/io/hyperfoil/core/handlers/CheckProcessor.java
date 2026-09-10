package io.hyperfoil.core.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.StreamReadFeature;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

/**
 * Validates a complete response body using one configured comparison strategy.
 */
public abstract class CheckProcessor implements Processor {
   private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
         .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
         .build();

   @Override
   public final void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      ensureDefragmented(isLastPart);
      if (!matches(data, offset, length)) {
         session.currentRequest().markInvalid();
      }
   }

   abstract boolean matches(ByteBuf data, int offset, int length);

   /**
    * Configures a check on a response body.
    *
    * <pre>
    * handler:
    *   body:
    *     check:
    *       equalTo: '{"result":1}'
    * </pre>
    *
    * Use <code>regex</code> or <code>json</code> instead of <code>equalTo</code> for the other matching strategies.
    * Exactly one strategy must be configured.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("check")
   public static class Builder implements Processor.Builder {
      private String equalTo;
      private String regex;
      private String json;
      private int configuredStrategies;

      /**
       * Requires the response body to have exactly these UTF-8 bytes, including its length.
       *
       * @param equalTo Expected response body.
       * @return This builder.
       */
      public Builder equalTo(String equalTo) {
         ++configuredStrategies;
         this.equalTo = equalTo;
         return this;
      }

      /**
       * Requires the UTF-8 response body to fully match this regular expression. Malformed input is decoded using
       * the Unicode replacement character.
       *
       * @param regex Expected regular expression.
       * @return This builder.
       */
      public Builder regex(String regex) {
         ++configuredStrategies;
         this.regex = regex;
         return this;
      }

      /**
       * Requires the response body to be structurally equal to this JSON value. Object property order is ignored,
       * array order is preserved, and numbers compare mathematically: <code>1</code>, <code>1.0</code>, <code>1e0</code>, and
       * negative zero are equal.
       *
       * @param json Expected JSON value.
       * @return This builder.
       */
      public Builder json(String json) {
         ++configuredStrategies;
         this.json = json;
         return this;
      }

      @Override
      public Processor build(boolean fragmented) {
         if (configuredStrategies != 1 || (equalTo == null && regex == null && json == null)) {
            throw new BenchmarkDefinitionException("Exactly one of equalTo, regex, or json must be set.");
         }

         CheckProcessor processor;
         if (equalTo != null) {
            processor = new EqualToProcessor(equalTo.getBytes(StandardCharsets.UTF_8));
         } else if (regex != null) {
            processor = new RegexProcessor(compileRegex(regex));
         } else {
            processor = new JsonProcessor(parseExpectedJson(json));
         }
         return DefragProcessor.of(processor, fragmented);
      }

      private static Pattern compileRegex(String regex) {
         try {
            return Pattern.compile(regex);
         } catch (PatternSyntaxException e) {
            throw new BenchmarkDefinitionException("Invalid check regex.", e);
         }
      }

      private static JsonMatcher parseExpectedJson(String json) {
         try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() == null) {
               throw new IOException("JSON value is missing.");
            }
            JsonMatcher matcher = parseExpectedValue(parser);
            if (parser.nextToken() != null) {
               throw new IOException("Trailing JSON content.");
            }
            return matcher;
         } catch (IOException e) {
            throw new BenchmarkDefinitionException("Invalid check JSON.", e);
         }
      }
   }

   private static class EqualToProcessor extends CheckProcessor {
      private final byte[] expected;

      private EqualToProcessor(byte[] expected) {
         this.expected = expected;
      }

      @Override
      boolean matches(ByteBuf data, int offset, int length) {
         if (length != expected.length) {
            return false;
         }
         for (int i = 0; i < length; ++i) {
            if (data.getByte(offset + i) != expected[i]) {
               return false;
            }
         }
         return true;
      }
   }

   private static class RegexProcessor extends CheckProcessor {
      private final Pattern expected;

      private RegexProcessor(Pattern expected) {
         this.expected = expected;
      }

      @Override
      boolean matches(ByteBuf data, int offset, int length) {
         return expected.matcher(data.toString(offset, length, StandardCharsets.UTF_8)).matches();
      }
   }

   private static class JsonProcessor extends CheckProcessor {
      private final JsonMatcher expected;

      private JsonProcessor(JsonMatcher expected) {
         this.expected = expected;
      }

      @Override
      boolean matches(ByteBuf data, int offset, int length) {
         try (ByteBufInputStream input = new ByteBufInputStream(data.slice(offset, length), false);
               JsonParser parser = JSON_FACTORY.createParser((InputStream) input)) {
            return parser.nextToken() != null && expected.matches(parser) && parser.nextToken() == null;
         } catch (IOException e) {
            return false;
         }
      }
   }

   private static JsonMatcher parseExpectedValue(JsonParser parser) throws IOException {
      switch (parser.currentTokenId()) {
         case JsonTokenId.ID_START_OBJECT:
            return parseExpectedObject(parser);
         case JsonTokenId.ID_START_ARRAY:
            return parseExpectedArray(parser);
         case JsonTokenId.ID_STRING:
            return new JsonStringMatcher(parser.getText());
         case JsonTokenId.ID_NUMBER_INT:
            return new JsonNumberMatcher(new BigDecimal(parser.getBigIntegerValue()));
         case JsonTokenId.ID_NUMBER_FLOAT:
            return new JsonNumberMatcher(parser.getDecimalValue());
         case JsonTokenId.ID_TRUE:
            return JsonLiteralMatcher.TRUE;
         case JsonTokenId.ID_FALSE:
            return JsonLiteralMatcher.FALSE;
         case JsonTokenId.ID_NULL:
            return JsonLiteralMatcher.NULL;
         default:
            throw new IOException("Expected a JSON value.");
      }
   }

   private static JsonMatcher parseExpectedObject(JsonParser parser) throws IOException {
      Map<String, JsonMatcher> values = new LinkedHashMap<>();
      for (String name = parser.nextFieldName(); name != null; name = parser.nextFieldName()) {
         if (parser.nextToken() == null) {
            throw new IOException("Object field value is missing.");
         }
         values.put(name, parseExpectedValue(parser));
      }
      if (!parser.hasToken(JsonToken.END_OBJECT)) {
         throw new IOException("Expected an object field.");
      }
      return new JsonObjectMatcher(values);
   }

   private static JsonMatcher parseExpectedArray(JsonParser parser) throws IOException {
      List<JsonMatcher> values = new ArrayList<>();
      JsonToken token;
      while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
         if (token == null) {
            throw new IOException("Array value is missing.");
         }
         values.add(parseExpectedValue(parser));
      }
      return new JsonArrayMatcher(values);
   }

   /**
    * Matches the value at the parser's current token. On success the parser remains on the scalar token or the
    * matching container end token; on failure its position is unspecified.
    */
   private interface JsonMatcher extends Serializable {
      boolean matches(JsonParser parser) throws IOException;
   }

   private static final class JsonObjectMatcher implements JsonMatcher {
      private final Map<String, JsonMatcher> values;

      private JsonObjectMatcher(Map<String, JsonMatcher> values) {
         this.values = Map.copyOf(values);
      }

      @Override
      public boolean matches(JsonParser parser) throws IOException {
         if (!parser.isExpectedStartObjectToken()) {
            return false;
         }
         int matchedFields = 0;
         for (String fieldName = parser.nextFieldName(); fieldName != null; fieldName = parser.nextFieldName()) {
            JsonMatcher matcher = values.get(fieldName);
            if (matcher == null) {
               return false;
            }
            if (parser.nextToken() == null || !matcher.matches(parser)) {
               return false;
            }
            ++matchedFields;
         }
         return parser.hasToken(JsonToken.END_OBJECT) && matchedFields == values.size();
      }
   }

   private static final class JsonArrayMatcher implements JsonMatcher {
      private final List<JsonMatcher> values;

      private JsonArrayMatcher(List<JsonMatcher> values) {
         this.values = List.copyOf(values);
      }

      @Override
      public boolean matches(JsonParser parser) throws IOException {
         if (!parser.isExpectedStartArrayToken()) {
            return false;
         }
         for (JsonMatcher matcher : values) {
            JsonToken token = parser.nextToken();
            if (token == null || token == JsonToken.END_ARRAY || !matcher.matches(parser)) {
               return false;
            }
         }
         return parser.nextToken() == JsonToken.END_ARRAY;
      }
   }

   private static final class JsonStringMatcher implements JsonMatcher {
      private final String value;

      private JsonStringMatcher(String value) {
         this.value = value;
      }

      @Override
      public boolean matches(JsonParser parser) throws IOException {
         return parser.hasToken(JsonToken.VALUE_STRING) && value.equals(parser.getText());
      }
   }

   private enum JsonLiteralMatcher implements JsonMatcher {
      TRUE(JsonToken.VALUE_TRUE),
      FALSE(JsonToken.VALUE_FALSE),
      NULL(JsonToken.VALUE_NULL);

      private final JsonToken token;

      JsonLiteralMatcher(JsonToken token) {
         this.token = token;
      }

      @Override
      public boolean matches(JsonParser parser) {
         return parser.hasToken(token);
      }
   }

   private static final class JsonNumberMatcher implements JsonMatcher {
      private final BigDecimal value;

      private JsonNumberMatcher(BigDecimal value) {
         this.value = value;
      }

      @Override
      public boolean matches(JsonParser parser) throws IOException {
         int tokenId = parser.currentTokenId();
         if (tokenId == JsonTokenId.ID_NUMBER_INT) {
            return value.compareTo(new BigDecimal(parser.getBigIntegerValue())) == 0;
         }
         return tokenId == JsonTokenId.ID_NUMBER_FLOAT && value.compareTo(parser.getDecimalValue()) == 0;
      }
   }

}

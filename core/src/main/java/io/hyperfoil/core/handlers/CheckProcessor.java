package io.hyperfoil.core.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.StreamReadFeature;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

/**
 * Validates a complete response body using one configured comparison strategy.
 */
public abstract class CheckProcessor implements Processor {
   private static final Logger log = LogManager.getLogger(CheckProcessor.class);
   private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
         .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
         .build();

   @Override
   public final void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      ensureDefragmented(isLastPart);
      if (!matches(session, data, offset, length)) {
         Request request = session.currentRequest();
         if (request == null) {
            log.error("#{} No request in progress, cannot mark it invalid", session.uniqueId());
         } else {
            request.markInvalid();
         }
      }
   }

   abstract boolean matches(Session session, ByteBuf data, int offset, int length);

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

      /**
       * Requires the response body to have exactly these UTF-8 bytes, including its length.
       *
       * @param equalTo Expected response body.
       * @return This builder.
       */
      public Builder equalTo(String equalTo) {
         requireNoStrategy();
         this.equalTo = equalTo;
         return this;
      }

      /**
       * Requires the UTF-8 response body to fully match this regular expression. The dot matches line terminators
       * as well. Malformed input is decoded using the Unicode replacement character.
       *
       * @param regex Expected regular expression.
       * @return This builder.
       */
      public Builder regex(String regex) {
         requireNoStrategy();
         this.regex = regex;
         return this;
      }

      /**
       * Requires the response body to be structurally equal to this JSON value. Object property order is ignored,
       * array order is preserved, and numbers compare mathematically: <code>1</code>, <code>1.0</code>, <code>1e0</code>, and
       * negative zero are equal. A response body that repeats a property name within one object never matches.
       *
       * @param json Expected JSON value.
       * @return This builder.
       */
      public Builder json(String json) {
         requireNoStrategy();
         this.json = json;
         return this;
      }

      private void requireNoStrategy() {
         if (equalTo != null || regex != null || json != null) {
            throw new BenchmarkDefinitionException("Only one of equalTo, regex, or json can be set.");
         }
      }

      @Override
      public Processor build(boolean fragmented) {
         CheckProcessor processor;
         if (equalTo != null) {
            processor = new EqualToProcessor(equalTo.getBytes(StandardCharsets.UTF_8));
         } else if (regex != null) {
            processor = new RegexProcessor(compileRegex(regex));
         } else if (json != null) {
            processor = new JsonProcessor(parseExpectedJson(json));
         } else {
            throw new BenchmarkDefinitionException("One of equalTo, regex, or json must be set.");
         }
         return DefragProcessor.of(processor, fragmented);
      }

      private static Pattern compileRegex(String regex) {
         try {
            return Pattern.compile(regex, Pattern.DOTALL);
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
      // ByteBuf is not serializable; the wrapper is recreated lazily after deserialization. Racing threads may
      // each wrap the array once, which is harmless as the wrapper is immutable.
      private transient ByteBuf expectedBuf;

      private EqualToProcessor(byte[] expected) {
         this.expected = expected;
      }

      @Override
      boolean matches(Session session, ByteBuf data, int offset, int length) {
         if (length != expected.length) {
            return false;
         }
         ByteBuf expectedBuf = this.expectedBuf;
         if (expectedBuf == null) {
            this.expectedBuf = expectedBuf = Unpooled.wrappedBuffer(expected);
         }
         return ByteBufUtil.equals(data, offset, expectedBuf, 0, length);
      }
   }

   private static class RegexProcessor extends CheckProcessor
         implements ResourceUtilizer, Session.ResourceKey<RegexProcessor.Context> {
      private final Pattern expected;

      private RegexProcessor(Pattern expected) {
         this.expected = expected;
      }

      @Override
      boolean matches(Session session, ByteBuf data, int offset, int length) {
         return session.getResource(this).matches(data, offset, length);
      }

      @Override
      public void reserve(Session session) {
         session.declareResource(this, Context::new);
      }

      class Context implements Session.Resource {
         private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
               .onMalformedInput(CodingErrorAction.REPLACE)
               .onUnmappableCharacter(CodingErrorAction.REPLACE);
         private final Matcher matcher = expected.matcher("");
         // UTF-8 decoding never produces more chars than input bytes, so the char buffer is sized to the body.
         private CharBuffer chars = CharBuffer.allocate(256);
         private ByteBuffer copy = ByteBuffer.allocate(256);

         boolean matches(ByteBuf data, int offset, int length) {
            ByteBuffer input;
            if (data.nioBufferCount() == 1) {
               input = data.internalNioBuffer(offset, length);
            } else {
               if (copy.capacity() < length) {
                  copy = ByteBuffer.allocate(length);
               }
               copy.clear().limit(length);
               data.getBytes(offset, copy);
               copy.flip();
               input = copy;
            }
            if (chars.capacity() < length) {
               chars = CharBuffer.allocate(length);
            }
            chars.clear();
            decoder.reset();
            CoderResult result = decoder.decode(input, chars, true);
            assert result.isUnderflow() : result;
            result = decoder.flush(chars);
            assert result.isUnderflow() : result;
            chars.flip();
            return matcher.reset(chars).matches();
         }
      }
   }

   private static class JsonProcessor extends CheckProcessor {
      private final JsonMatcher expected;

      private JsonProcessor(JsonMatcher expected) {
         this.expected = expected;
      }

      @Override
      boolean matches(Session session, ByteBuf data, int offset, int length) {
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
         // Duplicate field names are rejected by the parser, so counting matched fields is sufficient.
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
      private final char[] value;

      private JsonStringMatcher(String value) {
         this.value = value.toCharArray();
      }

      @Override
      public boolean matches(JsonParser parser) throws IOException {
         if (!parser.hasToken(JsonToken.VALUE_STRING) || parser.getTextLength() != value.length) {
            return false;
         }
         // Compares against the parser's internal buffer without materializing a String.
         int offset = parser.getTextOffset();
         return Arrays.equals(parser.getTextCharacters(), offset, offset + value.length, value, 0, value.length);
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
      private final boolean isLong;
      private final long longValue;

      private JsonNumberMatcher(BigDecimal value) {
         this.value = value;
         boolean isLong;
         long longValue;
         try {
            longValue = value.longValueExact();
            isLong = true;
         } catch (ArithmeticException e) {
            longValue = 0;
            isLong = false;
         }
         this.isLong = isLong;
         this.longValue = longValue;
      }

      @Override
      public boolean matches(JsonParser parser) throws IOException {
         switch (parser.currentTokenId()) {
            case JsonTokenId.ID_NUMBER_INT:
               switch (parser.getNumberType()) {
                  case INT:
                  case LONG:
                     // Common case: no BigInteger/BigDecimal allocation.
                     return isLong && parser.getLongValue() == longValue;
                  default:
                     return value.compareTo(new BigDecimal(parser.getBigIntegerValue())) == 0;
               }
            case JsonTokenId.ID_NUMBER_FLOAT:
               return value.compareTo(parser.getDecimalValue()) == 0;
            default:
               return false;
         }
      }
   }

}

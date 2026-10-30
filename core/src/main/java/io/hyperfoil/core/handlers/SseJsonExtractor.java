package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.vertx.core.json.JsonObject;

/**
 * Extracts arbitrary JSON fields from SSE (Server-Sent Events) responses.
 * Parses newline-delimited JSON events and extracts specified field(s) using JSON path notation.
 */
public class SseJsonExtractor implements Processor {
   private static final Logger log = LogManager.getLogger(SseJsonExtractor.class);
   private static final JsonFactory JSON_FACTORY = new JsonFactory();

   private final Map<String, String[]> targetPaths;
   private final byte[][] ignorePatternBytes;
   private final ObjectAccess toVar;
   private final ObjectAccess stateVar;
   private final int maxLineSize;

   private static class ExtractorState {
      final Map<String, StringBuilder> values = new LinkedHashMap<>();
      byte[] lineBuffer = new byte[256];
      int bufferLength = 0;
      private final int maxLineSize;

      ExtractorState(int maxLineSize) {
         this.maxLineSize = maxLineSize;
      }

      void append(Session session, ByteBuf buf, int offset, int length) {
         int required = bufferLength + length;
         if (required > maxLineSize) {
            session.fail(new IllegalStateException("Line exceeds maximum buffer size of " + maxLineSize
                  + " bytes. Possible malformed stream without newline terminators."));
            return;
         }

         if (required > lineBuffer.length) {
            // Amortized buffer expansion via bit shift, minimizing reallocation frequency.
            int nextSize = lineBuffer.length;
            while (nextSize < required) {
               nextSize <<= 1;
            }
            byte[] newBuf = new byte[nextSize];
            System.arraycopy(lineBuffer, 0, newBuf, 0, bufferLength);
            lineBuffer = newBuf;
         }

         buf.getBytes(offset, lineBuffer, bufferLength, length);
         bufferLength += length;
      }

      void resetValues() {
         for (Map.Entry<String, StringBuilder> entry : values.entrySet()) {
            StringBuilder sb = entry.getValue();
            // Recreate the StringBuilder if it grows over 64KB to avoid wasting memory.
            // sb.setLength(0) only resets the text, not the memory size. Without this check,
            // a single huge response would permanently increase memory usage for this session.
            if (sb.capacity() > 65536) {
               entry.setValue(new StringBuilder());
            } else {
               sb.setLength(0);
            }
         }
         if (lineBuffer.length > 65536) {
            lineBuffer = new byte[256];
         }
      }
   }

   public SseJsonExtractor(Map<String, String[]> targetPaths,
         List<String> ignorePatterns, ObjectAccess toVar, ObjectAccess stateVar, int maxLineSize) {
      this.targetPaths = targetPaths;

      this.ignorePatternBytes = new byte[ignorePatterns.size()][];
      for (int i = 0; i < ignorePatterns.size(); i++) {
         this.ignorePatternBytes[i] = ignorePatterns.get(i).getBytes(StandardCharsets.UTF_8);
      }

      this.toVar = toVar;
      this.stateVar = stateVar;
      this.maxLineSize = maxLineSize;
   }

   @Override
   public void before(Session session) {
      ExtractorState state = null;
      if (stateVar.isSet(session)) {
         state = (ExtractorState) stateVar.getObject(session);
      }
      if (state == null) {
         state = new ExtractorState(maxLineSize);
         for (String key : targetPaths.keySet()) {
            state.values.put(key, new StringBuilder());
         }
         stateVar.setObject(session, state);
      } else {
         state.resetValues();
         state.bufferLength = 0;
      }
   }

   @Override
   public void process(Session session, ByteBuf byteBuf, int offset, int length, boolean isLastPart) {
      ExtractorState state = (ExtractorState) stateVar.getObject(session);
      if (state == null) {
         before(session);
         state = (ExtractorState) stateVar.getObject(session);
      }

      int currentOffset = offset;
      int endOffset = offset + length;

      while (currentOffset < endOffset) {
         int lfIndex = byteBuf.indexOf(currentOffset, endOffset, (byte) '\n');

         if (lfIndex != -1) {
            int chunkLen = lfIndex - currentOffset;
            state.append(session, byteBuf, currentOffset, chunkLen);

            processEvent(state.lineBuffer, state.bufferLength, state.values);
            state.bufferLength = 0;

            currentOffset = lfIndex + 1;
         } else {
            int remaining = endOffset - currentOffset;
            state.append(session, byteBuf, currentOffset, remaining);
            break;
         }
      }

      if (isLastPart) {
         if (state.bufferLength > 0) {
            processEvent(state.lineBuffer, state.bufferLength, state.values);
         }
         storeResults(session, state.values);
      }
   }

   private void processEvent(byte[] buffer, int length, Map<String, StringBuilder> values) {
      // In-place bounds adjustment to strip whitespace. Avoids String.trim() allocation overhead.
      int end = length;
      while (end > 0 && (buffer[end - 1] == '\r' || buffer[end - 1] == '\n' ||
            buffer[end - 1] == ' ' || buffer[end - 1] == '\t')) {
         end--;
      }

      int start = 0;
      while (start < end && (buffer[start] == ' ' || buffer[start] == '\t')) {
         start++;
      }

      if (end - start < 5) {
         return;
      }

      // Zero-allocation prefix match. Avoids new String(buffer).startsWith("data:") per event.
      if (buffer[start] == 'd' && buffer[start + 1] == 'a' && buffer[start + 2] == 't' &&
            buffer[start + 3] == 'a' && buffer[start + 4] == ':') {

         start += 5;

         if (start < end && buffer[start] == ' ') {
            start++;
         }

         int jsonLen = end - start;
         if (jsonLen == 0) {
            return;
         }

         // Pre-parser byte matching for terminal signals (e.g., [DONE]).
         // Prevents expensive Jackson JsonParseException stack traces.
         for (byte[] pattern : ignorePatternBytes) {
            if (matchesPattern(buffer, start, jsonLen, pattern)) {
               return;
            }
         }

         try (JsonParser parser = JSON_FACTORY.createParser(buffer, start, jsonLen)) {
            boolean matchedAny = false;

            while (!parser.isClosed()) {
               JsonToken token = parser.nextToken();
               if (token == null)
                  break;

               if (token.isScalarValue()) {
                  JsonStreamContext ctx = parser.getParsingContext();

                  for (Map.Entry<String, String[]> entry : targetPaths.entrySet()) {
                     if (matchesContext(ctx, entry.getValue())) {
                        String valueAsString = parser.getValueAsString();
                        if (valueAsString != null) {
                           values.get(entry.getKey()).append(valueAsString);
                        }
                        matchedAny = true;
                     }
                  }
               }
            }

            if (!matchedAny && log.isTraceEnabled()) {
               log.trace("No paths matched in SSE JSON event. Payload: {}",
                     new String(buffer, start, jsonLen, StandardCharsets.UTF_8));
            }

         } catch (Exception e) {
            log.error("Failed to parse SSE JSON event. Payload: {}",
                  new String(buffer, start, jsonLen, StandardCharsets.UTF_8), e);
         }
      }
   }

   private boolean matchesContext(JsonStreamContext ctx, String[] path) {
      int i = path.length - 1;
      while (ctx != null && !ctx.inRoot()) {
         if (i < 0)
            return false;
         if (ctx.inArray()) {
            if (!path[i].equals(Integer.toString(ctx.getCurrentIndex())))
               return false;
         } else if (ctx.inObject()) {
            String currentName = ctx.getCurrentName();
            if (currentName == null || !currentName.equals(path[i]))
               return false;
         }
         ctx = ctx.getParent();
         i--;
      }
      return i == -1;
   }

   private boolean matchesPattern(byte[] buffer, int offset, int length, byte[] pattern) {
      if (length != pattern.length) {
         return false;
      }
      for (int i = 0; i < length; i++) {
         if (buffer[offset + i] != pattern[i]) {
            return false;
         }
      }
      return true;
   }

   @Override
   public void after(Session session) {
      if (stateVar.isSet(session)) {
         ExtractorState state = (ExtractorState) stateVar.getObject(session);
         if (state != null) {
            storeResults(session, state.values);
         }
      }
   }

   private void storeResults(Session session, Map<String, StringBuilder> values) {
      if (targetPaths.size() == 1) {
         String key = targetPaths.keySet().iterator().next();
         toVar.setObject(session, values.get(key).toString());
      } else {
         JsonObject results = new JsonObject();
         for (Map.Entry<String, StringBuilder> entry : values.entrySet()) {
            results.put(entry.getKey(), entry.getValue().toString());
         }
         toVar.setObject(session, results);
      }
   }

   private static String[] parsePathSegments(String pathExpression) {
      List<String> segments = new ArrayList<>();
      int i = 0;
      int len = pathExpression.length();

      while (i < len) {
         char c = pathExpression.charAt(i);

         if (c == '.') {
            i++;
            continue;
         }

         if (c == '[') {
            i++;
            if (i >= len)
               throw new BenchmarkDefinitionException("Unclosed bracket in path: " + pathExpression);

            if (pathExpression.charAt(i) == '"' || pathExpression.charAt(i) == '\'') {
               char quote = pathExpression.charAt(i);
               i++;
               int start = i;
               while (i < len && pathExpression.charAt(i) != quote) {
                  i++;
               }
               if (i >= len)
                  throw new BenchmarkDefinitionException("Unclosed quote in path: " + pathExpression);
               segments.add(pathExpression.substring(start, i));
               i++;

               if (i >= len || pathExpression.charAt(i) != ']') {
                  throw new BenchmarkDefinitionException("Expected closing bracket in path: " + pathExpression);
               }
               i++;
            } else {
               int start = i;
               while (i < len && pathExpression.charAt(i) != ']') {
                  i++;
               }
               if (i >= len)
                  throw new BenchmarkDefinitionException("Unclosed bracket in path: " + pathExpression);
               String indexStr = pathExpression.substring(start, i);
               try {
                  Integer.parseInt(indexStr);
                  segments.add(indexStr);
               } catch (NumberFormatException e) {
                  throw new BenchmarkDefinitionException("Invalid array index in path: " + indexStr);
               }
               i++;
            }
         } else {
            int start = i;
            while (i < len && pathExpression.charAt(i) != '.' && pathExpression.charAt(i) != '[') {
               i++;
            }
            String segment = pathExpression.substring(start, i);
            if (!segment.isEmpty()) {
               segments.add(segment);
            }
         }
      }

      return segments.toArray(new String[0]);
   }

   @MetaInfServices(Processor.Builder.class)
   @Name("sseJsonExtractor")
   public static class Builder implements Processor.Builder {
      private String path;
      private Map<String, String> paths;
      private String toVar;
      private List<String> ignorePatterns = new ArrayList<>(Collections.singletonList("[DONE]"));
      private int maxLineSize = 1_048_576;

      public Builder path(String path) {
         this.path = path;
         return this;
      }

      public Builder paths(Map<String, String> paths) {
         this.paths = paths;
         return this;
      }

      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      public Builder ignorePatterns(List<String> ignorePatterns) {
         this.ignorePatterns = ignorePatterns;
         return this;
      }

      public Builder maxLineSize(int maxLineSize) {
         this.maxLineSize = maxLineSize;
         return this;
      }

      @Override
      public Processor build(boolean fragmented) {
         if (toVar == null || toVar.isEmpty()) {
            throw new BenchmarkDefinitionException("toVar cannot be null or empty");
         }

         Map<String, String> rawPathMap = new LinkedHashMap<>();

         if (path != null && !path.isEmpty()) {
            rawPathMap.put("value", path);
         } else if (paths != null && !paths.isEmpty()) {
            rawPathMap.putAll(paths);
         } else {
            throw new BenchmarkDefinitionException("Either 'path' or 'paths' must be specified");
         }

         Map<String, String[]> parsedPaths = new LinkedHashMap<>();
         for (Map.Entry<String, String> entry : rawPathMap.entrySet()) {
            parsedPaths.put(entry.getKey(), parsePathSegments(entry.getValue()));
         }

         String stateKey = "sseJsonExtractor." + UUID.randomUUID();

         return new SseJsonExtractor(
               parsedPaths,
               ignorePatterns,
               SessionFactory.objectAccess(toVar),
               SessionFactory.objectAccess(stateKey),
               maxLineSize);
      }
   }
}

package io.hyperfoil.core.handlers.html;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.util.Trie;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HtmlHandler implements Processor, ResourceUtilizer, Session.ResourceKey<HtmlHandler.Context> {
   private static final Logger log = LoggerFactory.getLogger(HtmlHandler.class);
   private static final boolean trace = log.isTraceEnabled();

   private final TagHandler[] handlers;

   private HtmlHandler(TagHandler... handlers) {
      this.handlers = handlers;
   }

   @Override
   public void before(Session session) {
      for (TagHandler h : handlers) {
         h.processor().before(session);
      }
   }

   @Override
   public void after(Session session) {
      for (TagHandler h : handlers) {
         h.processor().after(session);
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      Context ctx = session.getResource(this);
      switch (ctx.tagStatus) {
         case PARSING_TAG:
            ctx.tagStart = offset;
            break;
         case PARSING_ATTR:
            ctx.attrStart = offset;
            break;
         case PARSING_VALUE:
            ctx.valueStart = offset;
            break;
      }
      while (length > 0) {
         byte c = data.getByte(offset++);
         --length;
         switch (ctx.tagStatus) {
            case NO_TAG:
               if (c == '<') {
                  ctx.tagStatus = TagStatus.ENTERED;
               }
               break;
            case ENTERED:
               if (c == '!') {
                  ctx.tagStatus = TagStatus.DOCTYPE_START;
               } else if (Character.isWhitespace(c)) {
                  ctx.tagStatus = TagStatus.BEFORE_TAG;
               } else if (c == '/') {
                  ctx.tagClosing = true;
                  ctx.tagStatus = TagStatus.BEFORE_TAG;
               } else {
                  ctx.tagStart = offset - 1;
                  ctx.tagStatus = TagStatus.PARSING_TAG;
               }
               break;
            case DOCTYPE_START:
               if (c == '-') {
                  ctx.comment = 3;
                  ctx.tagStatus = TagStatus.COMMENT;
               } else {
                  ctx.tagStatus = TagStatus.DOCTYPE;
               }
               break;
            case DOCTYPE:
               if (c == '>') {
                  ctx.endTag(session);
               }
               break;
            case COMMENT:
               if (ctx.comment == 1) {
                  if (c == '>') {
                     ctx.comment = 0;
                     ctx.tagStatus = TagStatus.NO_TAG;
                  } else if (c != '-') {
                     ctx.comment = 3;
                  }
               } else if (ctx.comment > 0) {
                  if (c == '-') {
                     ctx.comment--;
                  }
               }
               break;
            case BEFORE_TAG:
               if (!Character.isWhitespace(c)) {
                  ctx.tagStatus = TagStatus.PARSING_TAG;
                  ctx.tagStart = offset - 1;
               }
               break;
            case PARSING_TAG:
               if (Character.isWhitespace(c)) {
                  ctx.onTag(session, ctx.tagClosing, data, offset - 1, true);
                  ctx.tagStatus = TagStatus.BEFORE_ATTR;
               } else if (c == '>') {
                  ctx.endTag(session);
               }
               break;
            case BEFORE_ATTR:
               if (c == '>') {
                  ctx.endTag(session);
               } else if (!Character.isWhitespace(c)) {
                  ctx.attrStart = offset - 1;
                  ctx.tagStatus = TagStatus.PARSING_ATTR;
               }
               break;
            case PARSING_ATTR:
               if (c == '=' || Character.isWhitespace(c)) {
                  ctx.onAttr(session, data, offset - 1, true);
                  ctx.tagStatus = TagStatus.BEFORE_VALUE;
               } else if (c == '>') {
                  ctx.onAttr(session, data, offset - 1, true);
                  ctx.endTag(session);
               }
               break;
            case BEFORE_VALUE:
               if (c == '>') {
                  ctx.endTag(session);
               } else if (c == '=' || Character.isWhitespace(c)) {
                  // ignore, there was a whitespace
                  break;
               } else if (c == '"') {
                  ctx.tagStatus = TagStatus.PARSING_VALUE;
                  ctx.valueStart = offset;
                  ctx.valueQuoted = true;
               } else {
                  // missing quotes
                  ctx.tagStatus = TagStatus.PARSING_VALUE;
                  ctx.valueStart = offset - 1;
               }
               break;
            case PARSING_VALUE:
               if (c == '\\') {
                  ctx.charEscaped = true;
               } else if (c == '"' && !ctx.charEscaped) {
                  ctx.onValue(session, data, offset - 1, true);
                  ctx.tagStatus = TagStatus.BEFORE_ATTR;
                  ctx.valueQuoted = false;
               } else if (!ctx.valueQuoted && Character.isWhitespace(c)) {
                  ctx.onValue(session, data, offset - 1, true);
                  ctx.tagStatus = TagStatus.BEFORE_ATTR;
               } else {
                  ctx.charEscaped = false;
               }
               break;
            default:
               throw new IllegalStateException();
         }
      }
      switch (ctx.tagStatus) {
         case PARSING_TAG:
            ctx.onTag(session, ctx.tagClosing, data, offset - 1, false);
            break;
         case PARSING_ATTR:
            ctx.onAttr(session, data, offset - 1, false);
            break;
         case PARSING_VALUE:
            ctx.onValue(session, data, offset - 1, false);
            break;
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, Context::new);
      ResourceUtilizer.reserve(session, (Object[]) handlers);
   }

   interface TagHandler {
      Processor processor();

      HandlerContext newContext();
   }

   enum TagStatus {
      NO_TAG,
      ENTERED,
      BEFORE_TAG,
      PARSING_TAG,
      BEFORE_ATTR,
      PARSING_ATTR,
      DOCTYPE_START, // doctype, comment
      DOCTYPE,
      BEFORE_VALUE, PARSING_VALUE, COMMENT
   }

   class Context implements Session.Resource {
      TagStatus tagStatus = TagStatus.NO_TAG;
      boolean valueQuoted;
      boolean charEscaped;
      boolean tagClosing;
      int tagStart = -1;
      int attrStart = -1;
      int valueStart = -1;
      int comment;
      HandlerContext[] handlerCtx;

      Context() {
         handlerCtx = Stream.of(handlers).map(TagHandler::newContext).toArray(HandlerContext[]::new);
      }

      void onTag(Session session, boolean close, ByteBuf data, int tagEnd, boolean isLast) {
         assert tagStart >= 0;
         for (HandlerContext handlerCtx : handlerCtx) {
            handlerCtx.onTag(session, close, data, tagStart, tagEnd - tagStart, isLast);
         }
         tagStart = -1;
      }

      void onAttr(Session session, ByteBuf data, int attrEnd, boolean isLast) {
         assert attrStart >= 0;
         for (HandlerContext handlerCtx : handlerCtx) {
            handlerCtx.onAttr(session, data, attrStart, attrEnd - attrStart, isLast);
         }
         attrStart = -1;
      }

      void onValue(Session session, ByteBuf data, int valueEnd, boolean isLast) {
         assert valueStart >= 0;
         for (HandlerContext handlerCtx : handlerCtx) {
            handlerCtx.onValue(session, data, valueStart, valueEnd - valueStart, isLast);
         }
         valueStart = -1;
      }

      // TODO: content handling

      private void endTag(Session session) {
         tagStatus = TagStatus.NO_TAG;
         tagClosing = false;
         for (int i = 0; i < handlerCtx.length; ++i) {
            handlerCtx[i].endTag(session);
         }
      }
   }

   interface HandlerContext {
      void onTag(Session session, boolean close, ByteBuf data, int offset, int length, boolean isLast);

      void onAttr(Session session, ByteBuf data, int offset, int length, boolean isLast);

      void onValue(Session session, ByteBuf data, int offset, int length, boolean isLast);

      void endTag(Session session);
   }

   /**
    * Parses HTML tags and invokes handlers based on criteria.
    */
   @MetaInfServices(HttpRequestProcessorBuilder.class)
   @Name("parseHtml")
   public static class Builder implements HttpRequestProcessorBuilder {
      private EmbeddedResourceHandlerBuilder embeddedResourceHandler;

      /**
       * Handler firing upon reference to other resource, e.g. image, stylesheet...
       *
       * @return Builder.
       */
      public EmbeddedResourceHandlerBuilder onEmbeddedResource() {
         if (embeddedResourceHandler != null) {
            throw new BenchmarkDefinitionException("Embedded resource handler already set!");
         }
         return embeddedResourceHandler = new EmbeddedResourceHandlerBuilder();
      }

      @Override
      public void prepareBuild() {
         embeddedResourceHandler.prepareBuild();
      }

      @Override
      public HtmlHandler build(boolean fragmented) {
         return new HtmlHandler(embeddedResourceHandler.build());
      }
   }

   static class BaseTagAttributeHandler implements TagHandler, ResourceUtilizer {
      private final Trie trie;
      private final byte[][] attributes;
      private final Processor processor;

      BaseTagAttributeHandler(String[] tags, String[] attributes, Processor processor) {
         this.processor = processor;
         if (tags.length != attributes.length) {
            throw new IllegalArgumentException();
         }
         this.trie = new Trie(tags);
         this.attributes = Stream.of(attributes)
               .map(s -> s.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
      }

      @Override
      public Processor processor() {
         return processor;
      }

      @Override
      public HandlerContext newContext() {
         return new Ctx();
      }

      @Override
      public void reserve(Session session) {
         ResourceUtilizer.reserve(session, processor);
      }

      protected class Ctx implements HandlerContext {
         private final Trie.State trieState = trie.newState();
         private int tagMatched = -1;
         private int attrMatchedIndex = -1;
         private final ByteBuf valueBuffer = ByteBufAllocator.DEFAULT.buffer();

         @Override
         public void onTag(Session session, boolean close, ByteBuf data, int offset, int length, boolean isLast) {
            for (int i = 0; i < length; ++i) {
               int terminal = trieState.next(data.getByte(offset + i));
               if (isLast && terminal >= 0) {
                  tagMatched = terminal;
                  attrMatchedIndex = 0;
               }
            }
         }

         @Override
         public void onAttr(Session session, ByteBuf data, int offset, int length, boolean isLast) {
            if (tagMatched < 0) {
               return;
            }
            if (attrMatchedIndex >= 0) {
               for (int i = 0; i < length; ++i) {
                  if (attrMatchedIndex >= attributes[tagMatched].length) {
                     attrMatchedIndex = -1;
                     break;
                  } else if (attributes[tagMatched][attrMatchedIndex] == data.getByte(offset + i)) {
                     attrMatchedIndex++;
                  } else {
                     attrMatchedIndex = -1;
                     break;
                  }
               }
            }
            if (isLast) {
               if (attrMatchedIndex != attributes[tagMatched].length) {
                  attrMatchedIndex = 0;
               } // otherwise keep matched positive for value
            }
         }

         @Override
         public void onValue(Session session, ByteBuf data, int offset, int length, boolean isLast) {
            if (tagMatched < 0 || attrMatchedIndex <= 0) {
               return;
            }
            valueBuffer.ensureWritable(length);
            valueBuffer.writeBytes(data, offset, length);
            if (isLast) {
               processor().process(session, valueBuffer, valueBuffer.readerIndex(), valueBuffer.readableBytes(), true);
               valueBuffer.clear();
               attrMatchedIndex = 0;
            }
         }

         @Override
         public void endTag(Session session) {
            trieState.reset();
            tagMatched = -1;
            attrMatchedIndex = -1;
         }
      }
   }

}

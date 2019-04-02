package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.Processor;
import io.hyperfoil.api.http.BodyHandler;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.core.steps.AwaitIntStep;
import io.hyperfoil.core.steps.AwaitSequenceVarStep;
import io.hyperfoil.core.steps.HttpRequestStep;
import io.hyperfoil.core.steps.PathStatisticsSelector;
import io.hyperfoil.core.steps.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.steps.UnsetStep;
import io.hyperfoil.core.util.Trie;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiFunction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HtmlHandler implements BodyHandler, ResourceUtilizer, Session.ResourceKey<HtmlHandler.Context> {
   private static final Logger log = LoggerFactory.getLogger(HtmlHandler.class);
   private static final boolean trace = log.isTraceEnabled();

   private final TagHandler[] handlers;

   private HtmlHandler(TagHandler... handlers) {
      this.handlers = handlers;
   }

   @Override
   public void beforeData(HttpRequest request) {
      for (TagHandler h : handlers) {
         h.processor().before(request);
      }
   }

   @Override
   public void afterData(HttpRequest request) {
      for (TagHandler h : handlers) {
         h.processor().after(request);
      }
   }

   @Override
   public void handleData(HttpRequest request, ByteBuf data) {
      Context ctx = request.session.getResource(this);
      switch (ctx.tagStatus) {
         case PARSING_TAG:
            ctx.tagStart = data.readerIndex();
            break;
         case PARSING_ATTR:
            ctx.attrStart = data.readerIndex();
            break;
         case PARSING_VALUE:
            ctx.valueStart = data.readerIndex();
            break;
      }
      while (data.isReadable()) {
         byte c = data.readByte();
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
                  ctx.tagStart = data.readerIndex() - 1;
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
                  ctx.endTag(request);
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
                  ctx.tagStart = data.readerIndex() - 1;
               }
               break;
            case PARSING_TAG:
               if (Character.isWhitespace(c)) {
                  ctx.onTag(request, ctx.tagClosing, data, true);
                  ctx.tagStatus = TagStatus.BEFORE_ATTR;
               } else if (c == '>') {
                  ctx.endTag(request);
               }
               break;
            case BEFORE_ATTR:
               if (c == '>') {
                  ctx.endTag(request);
               } else if (!Character.isWhitespace(c)) {
                  ctx.attrStart = data.readerIndex() - 1;
                  ctx.tagStatus = TagStatus.PARSING_ATTR;
               }
               break;
            case PARSING_ATTR:
               if (c == '=' || Character.isWhitespace(c)) {
                  ctx.onAttr(request, data, true);
                  ctx.tagStatus = TagStatus.BEFORE_VALUE;
               } else if (c == '>') {
                  ctx.onAttr(request, data, true);
                  ctx.endTag(request);
               }
               break;
            case BEFORE_VALUE:
               if (c == '>') {
                  ctx.endTag(request);
               } else if (c == '=' || Character.isWhitespace(c)) {
                  // ignore, there was a whitespace
                  break;
               } else if (c == '"') {
                  ctx.tagStatus = TagStatus.PARSING_VALUE;
                  ctx.valueStart = data.readerIndex();
                  ctx.valueQuoted = true;
               } else {
                  // missing quotes
                  ctx.tagStatus = TagStatus.PARSING_VALUE;
                  ctx.valueStart = data.readerIndex() - 1;
               }
               break;
            case PARSING_VALUE:
               if (c == '\\') {
                  ctx.charEscaped = true;
               } else if (c == '"' && !ctx.charEscaped) {
                  ctx.onValue(request, data, true);
                  ctx.tagStatus = TagStatus.BEFORE_ATTR;
                  ctx.valueQuoted = false;
               } else if (!ctx.valueQuoted && Character.isWhitespace(c)) {
                  ctx.onValue(request, data, true);
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
            ctx.onTag(request, ctx.tagClosing, data, false);
            break;
         case PARSING_ATTR:
            ctx.onAttr(request, data, false);
            break;
         case PARSING_VALUE:
            ctx.onValue(request, data, false);
            break;
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, new Context());
      for (TagHandler handler : handlers) {
         if (handler instanceof ResourceUtilizer) {
            ((ResourceUtilizer) handler).reserve(session);
         }
      }
   }

   interface TagHandler {
      Processor<HttpRequest> processor();
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

      void onTag(HttpRequest request, boolean close, ByteBuf data, boolean isLast) {
         assert tagStart >= 0;
         for (HandlerContext handlerCtx : handlerCtx) {
            handlerCtx.onTag(request, close, data, tagStart, data.readerIndex() - 1 - tagStart, isLast);
         }
         tagStart = -1;
      }

      void onAttr(HttpRequest request, ByteBuf data, boolean isLast) {
         assert attrStart >= 0;
         for (HandlerContext handlerCtx : handlerCtx) {
            handlerCtx.onAttr(request, data, attrStart, data.readerIndex() - 1 - attrStart, isLast);
         }
         attrStart = -1;
      }

      void onValue(HttpRequest request, ByteBuf data, boolean isLast) {
         assert valueStart >= 0;
         for (HandlerContext handlerCtx : handlerCtx) {
            handlerCtx.onValue(request, data, valueStart, data.readerIndex() - 1 - valueStart, isLast);
         }
         valueStart = -1;
      }

      // TODO: content handling

      private void endTag(HttpRequest request) {
         tagStatus = TagStatus.NO_TAG;
         tagClosing = false;
         for (int i = 0; i < handlerCtx.length; ++i) {
            handlerCtx[i].endTag(request);
         }
      }
   }

   interface HandlerContext {
      void onTag(HttpRequest request, boolean close, ByteBuf data, int offset, int length, boolean isLast);
      void onAttr(HttpRequest request, ByteBuf data, int offset, int length, boolean isLast);
      void onValue(HttpRequest request, ByteBuf data, int offset, int length, boolean isLast);
      void endTag(HttpRequest request);
   }

   public static class Builder implements BodyHandler.Builder {
      private final Locator locator;
      private EmbeddedResourceHandlerBuilder embeddedResourceHandler;

      protected Builder(Locator locator) {
         this.locator = locator;
      }

      public EmbeddedResourceHandlerBuilder onEmbeddedResource() {
         if (embeddedResourceHandler != null) {
            throw new BenchmarkDefinitionException("Embedded resource handler already set!");
         }
         return embeddedResourceHandler = new EmbeddedResourceHandlerBuilder(locator);
      }

      @Override
      public void prepareBuild() {
         embeddedResourceHandler.prepareBuild();
      }

      @Override
      public BodyHandler.Builder copy(Locator locator) {
         Builder newBuilder = new Builder(locator);
         newBuilder.embeddedResourceHandler = embeddedResourceHandler.copy(locator);
         return newBuilder;
      }

      @Override
      public BodyHandler build() {
         return new HtmlHandler(embeddedResourceHandler.build());
      }
   }

   @MetaInfServices(BodyHandler.BuilderFactory.class)
   public static class BuilderFactory implements BodyHandler.BuilderFactory {
      @Override
      public String name() {
         return "parseHtml";
      }

      @Override
      public boolean acceptsParam() {
         return false;
      }

      @Override
      public BodyHandler.Builder newBuilder(Locator locator, String param) {
         if (param != null) {
            throw new BenchmarkDefinitionException(HtmlHandler.class.getName() + " does not accept inline parameter");
         }
         return new Builder(locator);
      }
   }

   /**
    * Handles <img src="...">, <link href="...">, <embed src="...">, <frame src="...">,
    *         <iframe src="...">, <object data="...">, <script src="...">
    *
    * Does not handle <source src="..."> or <track src="..."> because browser would choose only one of the options.
    */
   public static class EmbeddedResourceHandlerBuilder implements BuilderBase<EmbeddedResourceHandlerBuilder> {
      private static final String[] TAGS = { "img", "link", "embed", "frame", "iframe", "object", "script" };
      private static final String[] ATTRS = { "src", "href", "src", "src", "src", "data", "src" };

      private final Locator locator;
      private boolean ignoreExternal = true;
      private Processor.Builder<HttpRequest> processor;
      private FetchResourceBuilder fetchResource;

      EmbeddedResourceHandlerBuilder(Locator locator) {
         this.locator = locator;
      }

      public EmbeddedResourceHandlerBuilder ignoreExternal(boolean ignoreExternal) {
         this.ignoreExternal = ignoreExternal;
         return this;
      }

      public FetchResourceBuilder fetchResource() {
         return this.fetchResource = new FetchResourceBuilder(locator);
      }

      public EmbeddedResourceHandlerBuilder processor(Processor.Builder<HttpRequest> processor) {
         if (this.processor != null) {
            throw new BenchmarkDefinitionException("Processor already set! " + processor);
         }
         this.processor = processor;
         return this;
      }

      public ServiceLoadedBuilderProvider<Processor.Builder<HttpRequest>, HttpRequest.ProcessorBuilderFactory> processor() {
         return new ServiceLoadedBuilderProvider<>(HttpRequest.ProcessorBuilderFactory.class, locator, this::processor);
      }

      public void prepareBuild() {
         if (fetchResource != null) {
            fetchResource.prepareBuild();
         }
      }

      @Override
      public EmbeddedResourceHandlerBuilder copy(Locator locator) {
         EmbeddedResourceHandlerBuilder builder = new EmbeddedResourceHandlerBuilder(locator);
         builder.ignoreExternal(ignoreExternal).processor(processor);
         if (fetchResource != null) {
            builder.fetchResource = fetchResource.copy(locator);
         }
         return builder;
      }

      public BaseTagAttributeHandler build() {
         if (processor != null && fetchResource != null) {
            throw new BenchmarkDefinitionException("Only one of processor/fetchResource allowed!");
         }
         Processor<HttpRequest> p;
         if (fetchResource != null) {
            p = fetchResource.build();
         } else if (processor != null) {
            p = processor.build();
         } else {
            throw new BenchmarkDefinitionException("Embedded resource handler is missing the processor");
         }
         return new BaseTagAttributeHandler(TAGS, ATTRS, new EmbeddedResourceProcessor(ignoreExternal, p));
      }
   }

   public static class FetchResourceBuilder implements BuilderBase<FetchResourceBuilder> {
      private final Locator locator;
      private final String generatedSeqName;

      private int maxResources;
      private SerializableBiFunction<String,String,String> statisticsSelector;
      private Action.Builder onCompletion;

      FetchResourceBuilder(Locator locator) {
         this.locator = locator;
         this.generatedSeqName = String.format("%s_fetchResources_%08x",
               locator.sequence().name(), ThreadLocalRandom.current().nextInt());
      }

      private String completionLatch() {
         return generatedSeqName + "_latch";
      }

      private String downloadUrlVar() {
         return generatedSeqName + "_url";
      }

      public FetchResourceBuilder maxResources(int maxResources) {
         this.maxResources = maxResources;
         return this;
      }

      public PathStatisticsSelector statistics() {
         PathStatisticsSelector statisticsSelector = new PathStatisticsSelector();
         statistics(statisticsSelector);
         return statisticsSelector;
      }

      public FetchResourceBuilder statistics(SerializableBiFunction<String,String,String> statistics) {
         if (this.statisticsSelector != null) {
            throw new BenchmarkDefinitionException("Statistics already set!");
         }
         this.statisticsSelector = statistics;
         return this;
      }

      public ServiceLoadedBuilderProvider<Action.Builder, Action.BuilderFactory> onCompletion() {
         return new ServiceLoadedBuilderProvider<>(Action.BuilderFactory.class, locator, this::onCompletion);
      }

      public FetchResourceBuilder onCompletion(Action.Builder a) {
         if (onCompletion != null) {
            throw new BenchmarkDefinitionException("Completion action already set!");
         }
         onCompletion = a;
         return this;
      }

      public void prepareBuild() {
         if (maxResources <= 0) {
            throw new BenchmarkDefinitionException("maxResources is missing or invalid.");
         }

         SequenceBuilder sequence = locator.scenario().sequence(generatedSeqName);

         // Constructor adds self into sequence
         HttpRequestStep.Builder requestBuilder = new HttpRequestStep.Builder(sequence).method(HttpMethod.GET);
         new StringGeneratorBuilder<>(requestBuilder, requestBuilder::pathGenerator)
               .sequenceVar(downloadUrlVar()); // this sets the pathGenerator
         if (statisticsSelector != null) {
            requestBuilder.statistics(statisticsSelector);
         } else {
            // Rather than using auto-generated sequence name we'll use the full path
            requestBuilder.statistics((baseUrl, path) -> baseUrl != null ? baseUrl + path : path);
         }
         requestBuilder.handler().onCompletion(new UnsetStep(downloadUrlVar(), true)::run);

         sequence
               .step(new AwaitSequenceVarStep("!" + downloadUrlVar()))
               .step(s -> {
                  s.addToInt(completionLatch(), -1);
                  return true;
               });

         Action onCompletion = this.onCompletion.build();
         locator.sequence().insertAfter(locator.step())
               .step(new AwaitIntStep(completionLatch(), x -> x == 0))
               .step(s -> {
                  onCompletion.run(s);
                  return true;
               });
      }

      @Override
      public FetchResourceBuilder copy(Locator locator) {
         return new FetchResourceBuilder(locator)
               .maxResources(maxResources)
               .statistics(statisticsSelector)
               .onCompletion(onCompletion);
      }

      public Processor<HttpRequest> build() {
         return new FetchResourcesAdapter(completionLatch(), new NewSequenceProcessor(maxResources, generatedSeqName + "_cnt", downloadUrlVar(), generatedSeqName));
      }
   }

   private static class FetchResourcesAdapter implements Processor<HttpRequest>, ResourceUtilizer {
      private final String completionCounter;
      private final Processor<Request> delegate;

      private FetchResourcesAdapter(String completionCounter, Processor<Request> delegate) {
         this.completionCounter = completionCounter;
         this.delegate = delegate;
      }

      @Override
      public void before(HttpRequest request) {
         request.session.setInt(completionCounter, 1);
         delegate.before(request);
      }

      @Override
      public void process(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
         request.session.addToInt(completionCounter, 1);
         delegate.process(request, data, offset, length, isLastPart);
      }

      @Override
      public void after(HttpRequest request) {
         request.session.addToInt(completionCounter, -1);
         delegate.after(request);
      }

      @Override
      public void reserve(Session session) {
         session.declareInt(completionCounter);
         if (delegate instanceof ResourceUtilizer) {
            ((ResourceUtilizer) delegate).reserve(session);
         }
      }
   }

   private static class BaseTagAttributeHandler implements TagHandler, ResourceUtilizer {
      private final Trie trie;
      private final byte[][] attributes;
      private final Processor<HttpRequest> processor;

      BaseTagAttributeHandler(String[] tags, String[] attributes, Processor<HttpRequest> processor) {
         this.processor = processor;
         if (tags.length != attributes.length) {
            throw new IllegalArgumentException();
         }
         this.trie = new Trie(tags);
         this.attributes = Stream.of(attributes)
               .map(s -> s.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
      }

      @Override
      public Processor<HttpRequest> processor() {
         return processor;
      }

      @Override
      public HandlerContext newContext() {
         return new Ctx();
      }

      @Override
      public void reserve(Session session) {
         if (processor instanceof ResourceUtilizer) {
            ((ResourceUtilizer) processor).reserve(session);
         }
      }

      protected class Ctx implements HandlerContext {
         private final Trie.State trieState = trie.newState();
         private int tagMatched = -1;
         private int attrMatchedIndex = -1;
         private ByteBuf valueBuffer = ByteBufAllocator.DEFAULT.buffer();

         @Override
         public void onTag(HttpRequest request, boolean close, ByteBuf data, int offset, int length, boolean isLast) {
            for (int i = 0; i < length; ++i) {
               int terminal = trieState.next(data.getByte(offset + i));
               if (isLast && terminal >= 0) {
                  tagMatched = terminal;
                  attrMatchedIndex = 0;
               }
            }
         }

         @Override
         public void onAttr(HttpRequest request, ByteBuf data, int offset, int length, boolean isLast) {
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
         public void onValue(HttpRequest request, ByteBuf data, int offset, int length, boolean isLast) {
            if (tagMatched < 0 || attrMatchedIndex <= 0) {
               return;
            }
            valueBuffer.ensureWritable(length);
            valueBuffer.writeBytes(data, offset, length);
            if (isLast) {
               processor().process(request, valueBuffer, valueBuffer.readerIndex(), valueBuffer.readableBytes(), true);
               valueBuffer.clear();
               attrMatchedIndex = 0;
            }
         }

         @Override
         public void endTag(HttpRequest request) {
            trieState.reset();
            tagMatched = -1;
            attrMatchedIndex = -1;
         }
      }
   }

   private static class EmbeddedResourceProcessor extends Processor.BaseDelegating<HttpRequest> implements ResourceUtilizer {
      private static final byte[] HTTP_PREFIX = "http".getBytes(StandardCharsets.UTF_8);

      private final boolean ignoreExternal;

      EmbeddedResourceProcessor(boolean ignoreExternal, Processor<HttpRequest> delegate) {
         super(delegate);
         this.ignoreExternal = ignoreExternal;
      }

      @Override
      public void process(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
         assert isLastPart;
         // TODO: here we should normalize the URL, remove escapes etc...

         boolean isAbsolute = hasPrefix(data, offset, length, HTTP_PREFIX);
         if (isAbsolute) {
            if (ignoreExternal) {
               boolean external = true;
               for (byte[] baseUrl : request.session.httpDestinations().baseUrlBytes()) {
                  if (hasPrefix(data, offset, length, baseUrl)) {
                     external = false;
                     break;
                  }
               }
               if (external) {
                  if (trace) {
                     log.trace("#{} Ignoring external URL {}", request.session.uniqueId(), Util.toString(data, offset, length));
                  }
                  return;
               }
            }
            if (trace) {
               log.trace("#{} Matched URL {}", request.session.uniqueId(), Util.toString(data, offset, length));
            }
            delegate.process(request, data, offset, length, true);
         } else if (data.getByte(offset) == '/') {
            // No need to rewrite relative URL
            if (trace) {
               log.trace("#{} Matched URL {}", request.session.uniqueId(), Util.toString(data, offset, length));
            }
            delegate.process(request, data, offset, length, true);
         } else {
            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(request.path.length() + length);
            Util.string2byteBuf(request.path, buffer);
            for (int i = buffer.writerIndex() - 1; i >= 0; --i) {
               if (buffer.getByte(i) == '/') {
                  buffer.writerIndex(i + 1);
                  break;
               }
            }
            buffer.ensureWritable(length);
            buffer.writeBytes(data, offset, length);
            if (trace) {
               log.trace("#{} Rewritten relative URL to {}", request.session.uniqueId(), Util.toString(buffer, buffer.readerIndex(), buffer.readableBytes()));
            }
            delegate.process(request, buffer, buffer.readerIndex(), buffer.readableBytes(), true);
            buffer.release();
         }
      }

      private boolean hasPrefix(ByteBuf data, int offset, int length, byte[] baseUrl) {
         int i = 0;
         for (; i < baseUrl.length && i < length; i++) {
            if (data.getByte(offset + i) != baseUrl[i]) {
               return false;
            }
         }
         return i == baseUrl.length;
      }

      @Override
      public void reserve(Session session) {
         if (delegate instanceof ResourceUtilizer) {
            ((ResourceUtilizer) delegate).reserve(session);
         }
      }
   }
}

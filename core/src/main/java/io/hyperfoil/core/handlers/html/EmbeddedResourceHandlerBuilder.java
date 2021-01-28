package io.hyperfoil.core.handlers.html;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.handlers.MultiProcessor;

/**
 * Handles <code>&lt;img src="..."&gt;</code>, <code>&lt;link href="..."&gt;</code>,
 * <code>&lt;embed src="..."&gt;</code>, <code>&lt;frame src="..."&gt;</code>,
 * <code>&lt;iframe src="..."&gt;</code>, <code>&lt;object data="..."&gt;</code> and <code>&lt;script src="..."&gt;</code>.
 * <p>
 * Does not handle <code>&lt;source src="..."&gt;</code> or <code>&lt;track src="..."&gt;</code> because browser
 * would choose only one of the options.
 */
public class EmbeddedResourceHandlerBuilder implements HtmlHandler.TagHandlerBuilder<EmbeddedResourceHandlerBuilder> {
   private static final String[] TAGS = { "img", "link", "embed", "frame", "iframe", "object", "script" };
   private static final String[] ATTRS = { "src", "href", "src", "src", "src", "data", "src" };

   private boolean ignoreExternal = true;
   private Processor.Builder<?> processor;
   private FetchResourceHandler.Builder fetchResource;

   /**
    * Ignore resources hosted on servers that are not covered in the <code>http</code> section.
    *
    * @param ignoreExternal Ignore?
    * @return Self.
    */
   public EmbeddedResourceHandlerBuilder ignoreExternal(boolean ignoreExternal) {
      this.ignoreExternal = ignoreExternal;
      return this;
   }

   /**
    * Automatically download referenced resource.
    *
    * @return Builder.
    */
   public FetchResourceHandler.Builder fetchResource() {
      return this.fetchResource = new FetchResourceHandler.Builder();
   }

   public EmbeddedResourceHandlerBuilder processor(Processor.Builder<?> processor) {
      if (this.processor == null) {
         this.processor = processor;
      } else if (this.processor instanceof MultiProcessor.Builder) {
         MultiProcessor.Builder<?> multiprocessor = (MultiProcessor.Builder<?>) this.processor;
         multiprocessor.processor(processor);
      } else {
         this.processor = new MultiProcessor.Builder<>().processor(this.processor).processor(processor);
      }
      return this;
   }

   /**
    * Custom processor invoked pointing to attribute data - e.g. in case of <code>&lt;img&gt;</code> tag
    * the processor gets contents of the <code>src</code> attribute.
    *
    * @return Builder.
    */
   public ServiceLoadedBuilderProvider<HttpRequestProcessorBuilder> processor() {
      return new ServiceLoadedBuilderProvider<>(HttpRequestProcessorBuilder.class, this::processor);
   }

   @Override
   public HtmlHandler.BaseTagAttributeHandler build() {
      if (processor == null && fetchResource == null) {
         throw new BenchmarkDefinitionException("embedded resource handler must define either processor or fetchResource!");
      }
      Processor processor = this.processor != null ? this.processor.build(false) : null;
      FetchResourceHandler fetchResource = this.fetchResource != null ? this.fetchResource.build() : null;
      return new HtmlHandler.BaseTagAttributeHandler(TAGS, ATTRS, new EmbeddedResourceProcessor(ignoreExternal, processor, fetchResource));
   }
}

package io.hyperfoil.http.html;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
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
   private MultiProcessor.Builder<EmbeddedResourceHandlerBuilder, ?> processors = new MultiProcessor.Builder<>(this);
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

   /**
    * Custom processor invoked pointing to attribute data - e.g. in case of <code>&lt;img&gt;</code> tag
    * the processor gets contents of the <code>src</code> attribute.
    *
    * @return Builder.
    */
   public ServiceLoadedBuilderProvider<Processor.Builder> processor() {
      return processors().processor();
   }

   @Embed
   public MultiProcessor.Builder<EmbeddedResourceHandlerBuilder, ?> processors() {
      return processors;
   }

   @Override
   public HtmlHandler.BaseTagAttributeHandler build() {
      if (processors.isEmpty() && fetchResource == null) {
         throw new BenchmarkDefinitionException("embedded resource handler must define either processor or fetchResource!");
      }
      Processor processor = processors.isEmpty() ? null : processors.build(false);
      FetchResourceHandler fetchResource = this.fetchResource != null ? this.fetchResource.build() : null;
      return new HtmlHandler.BaseTagAttributeHandler(TAGS, ATTRS, new EmbeddedResourceProcessor(ignoreExternal, processor, fetchResource));
   }
}

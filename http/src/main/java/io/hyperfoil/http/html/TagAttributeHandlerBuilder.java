package io.hyperfoil.http.html;

import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.core.handlers.MultiProcessor;
import io.hyperfoil.core.handlers.StoreShortcuts;

public class TagAttributeHandlerBuilder
      implements HtmlHandler.TagHandlerBuilder<TagAttributeHandlerBuilder>, StoreShortcuts.Host {
   private String tag;
   private String attribute;
   @SuppressWarnings("FieldMayBeFinal")
   private MultiProcessor.Builder<TagAttributeHandlerBuilder, ?> processors = new MultiProcessor.Builder<>(this);
   @SuppressWarnings("FieldMayBeFinal")
   private StoreShortcuts<TagAttributeHandlerBuilder> storeShortcuts = new StoreShortcuts<>(this);

   @Embed
   public MultiProcessor.Builder<TagAttributeHandlerBuilder, ?> processors() {
      return processors;
   }

   @Embed
   public StoreShortcuts<TagAttributeHandlerBuilder> storeShortcuts() {
      return storeShortcuts;
   }

   /**
    * Name of the tag this handler should look for, e.g. <code>form</code>
    *
    * @param tag Name of the tag.
    * @return Self.
    */
   public TagAttributeHandlerBuilder tag(String tag) {
      this.tag = tag;
      return this;
   }

   /**
    * Name of the attribute in this element you want to process, e.g. <code>action</code>
    *
    * @param attribute Name of the attribute.
    * @return Self.
    */
   public TagAttributeHandlerBuilder attribute(String attribute) {
      this.attribute = attribute;
      return this;
   }

   @Override
   public HtmlHandler.BaseTagAttributeHandler build() {
      return new HtmlHandler.BaseTagAttributeHandler(new String[] { tag }, new String[] { attribute }, processors.build(false));
   }

   @Override
   public void accept(Processor.Builder processor) {
      processors.processor(processor);
   }
}

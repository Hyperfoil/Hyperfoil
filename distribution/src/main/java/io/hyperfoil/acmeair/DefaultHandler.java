package io.hyperfoil.acmeair;

import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.RequestProcessorBuilder;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.handlers.DefragProcessor;

public class DefaultHandler extends DefragProcessor{


    private static final Logger log = LoggerFactory.getLogger(DefaultHandler.class);

    public DefaultHandler(Processor processor) {
        super(processor);
    }

    // Make this builder loadable as service
    @MetaInfServices(RequestProcessorBuilder.class)
    // This is the step name that will be used in the YAML
    @Name("default")
    public static class Builder implements RequestProcessorBuilder, InitFromParam<Builder> {
        private Locator locator;
        private RequestProcessorBuilder processor;

        @Override
        public Builder setLocator(Locator locator) {
           this.locator = locator;
           return this;
        }

        @Override
        public DefaultHandler build(boolean fragmented) {
            // TODO Auto-generated method stub
            return new DefaultHandler(processor.build(fragmented));
        }

         public Builder processor(RequestProcessorBuilder processor) {
            this.processor = processor;
            return this;
         }

         /**
          * Pass the selected parts to another processor.
          *
          * @return Builder.
          */
         public ServiceLoadedBuilderProvider<RequestProcessorBuilder> processor() {
            return new ServiceLoadedBuilderProvider<>(RequestProcessorBuilder.class, locator, this::processor);
         }

        @Override
        public Builder init(String param) {
            // TODO Auto-generated method stub
            return null;
        }

    }

}

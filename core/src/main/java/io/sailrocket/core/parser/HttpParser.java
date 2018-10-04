package io.sailrocket.core.parser;

import io.sailrocket.core.builders.HttpBuilder;

class HttpParser extends AbstractMappingParser<HttpBuilder> {
   HttpParser() {
      register("baseUrl", new PropertyParser.String<>(HttpBuilder::baseUrl));
   }
}

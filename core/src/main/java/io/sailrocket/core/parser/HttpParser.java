package io.sailrocket.core.parser;

import io.sailrocket.core.builders.HttpBuilder;

class HttpParser extends AbstractMappingParser<HttpBuilder> {
   HttpParser() {
      register("baseUrl", new PropertyParser.String<>(HttpBuilder::baseUrl));
      register("repeatCookies", new PropertyParser.Boolean<>(HttpBuilder::repeatCookies));
      register("allowHttp1x", new PropertyParser.Boolean<>(HttpBuilder::allowHttp1x));
      register("allowHttp2", new PropertyParser.Boolean<>(HttpBuilder::allowHttp2));
   }
}

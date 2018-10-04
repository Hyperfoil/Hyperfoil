package io.sailrocket.core.parser;

abstract class AbstractMappingParser<T> extends AbstractParser<T, T> {
   @Override
   public void parse(Context ctx, T target) throws ConfigurationParserException {
      callSubBuilders(ctx, target);
   }
}

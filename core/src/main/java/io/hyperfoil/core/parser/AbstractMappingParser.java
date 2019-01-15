package io.hyperfoil.core.parser;

abstract class AbstractMappingParser<T> extends AbstractParser<T, T> {
   @Override
   public void parse(Context ctx, T target) throws ParserException {
      callSubBuilders(ctx, target);
   }
}

package io.sailrocket.core.parser;

import java.util.function.Function;

public class Adapter<A, B> implements Parser<A> {
   private final Function<A, B> adapter;
   private final Parser<B> parser;

   public Adapter(Function<A, B> adapter, Parser<B> parser) {
      this.adapter = adapter;
      this.parser = parser;
   }

   @Override
   public void parse(Context ctx, A target) throws ConfigurationParserException {
      parser.parse(ctx, adapter.apply(target));
   }
}

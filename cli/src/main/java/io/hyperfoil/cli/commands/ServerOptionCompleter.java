package io.hyperfoil.cli.commands;

import java.util.function.Function;
import java.util.stream.Stream;

import io.hyperfoil.client.RestClient;

public class ServerOptionCompleter extends HyperfoilOptionCompleter {
   public ServerOptionCompleter(Function<RestClient, Stream<String>> provider) {
      super(context -> context.client() == null ? Stream.empty() : provider.apply(context.client()));
   }
}

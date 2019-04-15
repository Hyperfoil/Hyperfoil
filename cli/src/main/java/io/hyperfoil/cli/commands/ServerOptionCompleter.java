package io.hyperfoil.cli.commands;

import java.util.function.Function;
import java.util.stream.Stream;

import org.aesh.command.completer.OptionCompleter;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCompleterData;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.client.RestClientException;

public class ServerOptionCompleter implements OptionCompleter<HyperfoilCompleterData> {
   private final Function<RestClient, Stream<String>> provider;

   public ServerOptionCompleter(Function<RestClient, Stream<String>> provider) {
      this.provider = provider;
   }

   @Override
   public void complete(HyperfoilCompleterData completerInvocation) {
      HyperfoilCliContext context = completerInvocation.getContext();
      if (context.client() == null) {
         return;
      }
      Stream<String> benchmarks;
      try {
         benchmarks = provider.apply(context.client());
      } catch (RestClientException e) {
         return;
      }
      String prefix = completerInvocation.getGivenCompleteValue();
      if (prefix != null) {
         benchmarks = benchmarks.filter(b -> b.startsWith(prefix));
      }
      benchmarks.forEach(completerInvocation::addCompleterValue);
   }
}

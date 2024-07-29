package io.hyperfoil.cli.commands;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public abstract class ParamsCommand extends BenchmarkCommand {
   // TODO: empty and null params don't work with current version of Aesh but the fix is on the way...
   @OptionGroup(name = "param", shortName = 'P', description = "Parameters in case the benchmark is a template. " +
         "Can be set multiple times. Use `-PFOO=` to set the parameter to empty value and `-PFOO` to remove it " +
         "and use default if available.")
   Map<String, String> params;

   @OptionList(name = "empty-params", shortName = 'E', description = "Template parameters that should be set to empty string.")
   List<String> emptyParams;

   @Option(name = "reset-params", shortName = 'r', description = "Reset all parameters in context.", hasValue = false)
   boolean resetParams;

   protected Map<String, String> getParams(HyperfoilCommandInvocation invocation) {
      Map<String, String> currentParams = resetParams ? new HashMap<>() : new HashMap<>(invocation.context().currentParams());
      if (resetParams) {
         invocation.context().setCurrentParams(Collections.emptyMap());
      }
      if (params != null) {
         params.forEach((key, value) -> {
            if (value == null) {
               currentParams.remove(key);
            } else {
               currentParams.put(key, value);
            }
         });
      }
      if (emptyParams != null) {
         emptyParams.forEach(param -> currentParams.put(param, ""));
      }
      return currentParams;
   }

   protected boolean readParams(HyperfoilCommandInvocation invocation, List<String> missingParams,
         Map<String, String> currentParams) {
      if (!missingParams.isEmpty()) {
         invocation.println("This benchmark is a template with these mandatory parameters that haven't been set:");
      }
      for (String param : missingParams) {
         invocation.print(param + ": ");
         try {
            currentParams.put(param, invocation.getShell().readLine());
         } catch (InterruptedException e) {
            return false;
         }
      }
      return true;
   }

   protected List<String> getMissingParams(Map<String, String> paramsWithDefaults, Map<String, String> currentParams) {
      return paramsWithDefaults.entrySet().stream()
            .filter(entry -> entry.getValue() == null)
            .map(Map.Entry::getKey)
            .filter(p -> !currentParams.containsKey(p))
            .collect(Collectors.toList());
   }
}

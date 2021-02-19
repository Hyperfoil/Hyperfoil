package io.hyperfoil.clustering.webcli;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.cli.commands.BaseEditCommand;

class WebBenchmarkData implements BenchmarkData {
   final List<String> files = new ArrayList<>();

   @Override
   public InputStream readFile(String file) {
      files.add(file);
      return BaseEditCommand.EMPTY_INPUT_STREAM;
   }

   @Override
   public Map<String, byte[]> files() {
      return Collections.emptyMap();
   }
}

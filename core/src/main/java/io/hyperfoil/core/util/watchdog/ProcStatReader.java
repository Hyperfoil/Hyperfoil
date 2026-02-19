package io.hyperfoil.core.util.watchdog;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface ProcStatReader {

   List<String> readLines() throws IOException;
}

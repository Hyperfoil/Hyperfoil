package io.hyperfoil.internal;

import java.nio.file.Path;

/**
 * This interface should decouple controller implementation (in clustering module) and its uses (e.g. CLI).
 * The controller should listen on {@link #host()}:{@link #port()} as usual.
 */
public interface Controller {
   String host();

   int port();

   void stop();

   interface Factory {
      Controller start(Path rootDir);
   }
}

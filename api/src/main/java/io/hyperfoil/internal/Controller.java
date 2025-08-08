package io.hyperfoil.internal;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This interface should decouple controller implementation (in clustering module) and its uses (e.g. CLI).
 * The controller should listen on {@link #host()}:{@link #port()} as usual.
 */
public interface Controller {
   Path DEFAULT_ROOT_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "hyperfoil");
   String DEPLOYER = Properties.get(Properties.DEPLOYER, "ssh");
   long DEPLOY_TIMEOUT = Properties.getLong(Properties.DEPLOY_TIMEOUT, 60000);
   Path ROOT_DIR = Properties.get(Properties.ROOT_DIR, Paths::get, DEFAULT_ROOT_DIR);
   Path BENCHMARK_DIR = Properties.get(Properties.BENCHMARK_DIR, Paths::get, ROOT_DIR.resolve("benchmark"));
   Path HOOKS_DIR = ROOT_DIR.resolve("hooks");
   Path RUN_DIR = Properties.get(Properties.RUN_DIR, Paths::get, ROOT_DIR.resolve("run"));
   Boolean PERSIST_HDR = Properties.getBoolean(Properties.CONTROLLER_PERSIST_HDR);

   String host();

   int port();

   void stop();

   interface Factory {
      Controller start(Path rootDir);
   }
}

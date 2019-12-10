package io.hyperfoil.internal;

import java.util.function.Function;

public interface Properties {
   String AGENT_DEBUG_PORT = "io.hyperfoil.agent.debug.port";
   String AGENT_DEBUG_SUSPEND = "io.hyperfoil.agent.debug.suspend";
   String AGENT_NAME = "io.hyperfoil.agent.name";
   String BENCHMARK_DIR = "io.hyperfoil.benchmarkdir";
   String CONTROLLER_CLUSTER_IP = "io.hyperfoil.controller.cluster.ip";
   String CONTROLLER_CLUSTER_PORT = "io.hyperfoil.controller.cluster.port";
   String CONTROLLER_EXTERNAL_URI = "io.hyperfoil.controller.external.uri";
   String CONTROLLER_HOST = "io.hyperfoil.controller.host";
   String CONTROLLER_LOG = "io.hyperfoil.controller.log.file";
   String CONTROLLER_LOG_LEVEL = "io.hyperfoil.controller.log.level";
   String CONTROLLER_PORT = "io.hyperfoil.controller.port";
   String DEPLOYER = "io.hyperfoil.deployer";
   String DEPLOY_TIMEOUT = "io.hyperfoil.deploy.timeout";
   String LOG4J2_CONFIGURATION_FILE = "log4j.configurationFile";
   String ROOT_DIR = "io.hyperfoil.rootdir";
   String RUN_DIR = "io.hyperfoil.rundir";
   String RUN_ID = "io.hyperfoil.runid";
   String TRIGGER_URL = "io.hyperfoil.trigger.url";

   static String get(String property, String def) {
      return get(property, Function.identity(), def);
   }

   static long getLong(String property, long def) {
      return get(property, Long::valueOf, def);
   }

   static int getInt(String property, int def) {
      return get(property, Integer::valueOf, def);
   }

   static <T> T get(String property, Function<String, T> f, T def) {
      String value = System.getProperty(property);
      if (value != null) {
         return f.apply(value);
      }
      value = System.getenv(property.replaceAll("\\.", "_").toUpperCase());
      if (value != null) {
         return f.apply(value);
      }
      return def;
   }
}

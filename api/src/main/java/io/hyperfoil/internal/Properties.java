package io.hyperfoil.internal;

import java.util.function.Function;

public interface Properties {
   String HYPERFOIL_STACKTRACE = "io.hyperfoil.stacktrace";
   String AGENT_DEBUG_PORT = "io.hyperfoil.agent.debug.port";
   String AGENT_DEBUG_SUSPEND = "io.hyperfoil.agent.debug.suspend";
   String AGENT_JAVA_EXECUTABLE = "io.hyperfoil.agent.java.executable";
   String AGENT_NAME = "io.hyperfoil.agent.name";
   String BENCHMARK_DIR = "io.hyperfoil.benchmarkdir";
   String CONTROLLER_CLUSTER_IP = "io.hyperfoil.controller.cluster.ip";
   String CONTROLLER_CLUSTER_PORT = "io.hyperfoil.controller.cluster.port";
   String CONTROLLER_EXTERNAL_URI = "io.hyperfoil.controller.external.uri";
   String CONTROLLER_HOST = "io.hyperfoil.controller.host";
   String CONTROLLER_KEYSTORE_PATH = "io.hyperfoil.controller.keystore.path";
   String CONTROLLER_KEYSTORE_PASSWORD = "io.hyperfoil.controller.keystore.password";
   String CONTROLLER_PEM_KEYS = "io.hyperfoil.controller.pem.keys";
   String CONTROLLER_PEM_CERTS = "io.hyperfoil.controller.pem.certs";
   String CONTROLLER_SECURED_VIA_PROXY = "io.hyperfoil.controller.secured.via.proxy";
   String CONTROLLER_PASSWORD = "io.hyperfoil.controller.password";
   String CONTROLLER_LOG = "io.hyperfoil.controller.log.file";
   String CONTROLLER_LOG_LEVEL = "io.hyperfoil.controller.log.level";
   String CONTROLLER_PORT = "io.hyperfoil.controller.port";
   String CPU_WATCHDOG_PERIOD = "io.hyperfoil.cpu.watchdog.period";
   String CPU_WATCHDOG_IDLE_THRESHOLD = "io.hyperfoil.cpu.watchdog.idle.threshold";
   String DEPLOYER = "io.hyperfoil.deployer";
   String DEPLOY_TIMEOUT = "io.hyperfoil.deploy.timeout";
   String JITTER_WATCHDOG_PERIOD = "io.hyperfoil.jitter.watchdog.period";
   String JITTER_WATCHDOG_THRESHOLD = "io.hyperfoil.jitter.watchdog.threshold";
   String LOG4J2_CONFIGURATION_FILE = "log4j.configurationFile";
   String LOAD_DIR = "io.hyperfoil.loaddir";
   String MAX_IN_MEMORY_RUNS = "io.hyperfoil.max.in.memory.runs";
   String NETTY_TRANSPORT = "io.hyperfoil.netty.transport";
   String ROOT_DIR = "io.hyperfoil.rootdir";
   String RUN_DIR = "io.hyperfoil.rundir";
   String RUN_ID = "io.hyperfoil.runid";
   String TRIGGER_URL = "io.hyperfoil.trigger.url";
   String CLI_REQUEST_TIMEOUT = "io.hyperfoil.cli.request.timeout";
   String GC_CHECK = "io.hyperfoil.gc.check.enabled";
   String CLUSTER_JGROUPS_STACK = "io.hyperfoil.cluster.jgroups_stack";
   String REPORT_TEMPLATE = "io.hyperfoil.report.template";

   static String get(String property, String def) {
      return get(property, Function.identity(), def);
   }

   static long getLong(String property, long def) {
      return get(property, Long::valueOf, def);
   }

   static int getInt(String property, int def) {
      return get(property, Integer::valueOf, def);
   }

   static boolean getBoolean(String property) {
      return get(property, Boolean::valueOf, false);
   }

   static <T> T get(String property, Function<String, T> f, T def) {
      String value = System.getProperty(property);
      if (value != null) {
         return f.apply(value);
      }
      value = System.getenv(property.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase());
      if (value != null) {
         return f.apply(value);
      }
      return def;
   }
}

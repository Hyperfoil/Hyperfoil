package io.hyperfoil.api.deployment;

public interface AgentProperties {
   String AGENT_NAME = "io.hyperfoil.agent.name";
   String DEPLOYER = "io.hyperfoil.deployer";
   String DEPLOY_TIMEOUT = "io.hyperfoil.deploy.timeout";
   String RUN_ID = "io.hyperfoil.runid";
   String AGENT_DEBUG_PORT = "io.hyperfoil.agent.debug.port";
   String AGENT_DEBUG_SUSPEND = "io.hyperfoil.agent.debug.suspend";
   String CONTROLLER_CLUSTER_IP = "io.hyperfoil.controller.cluster.ip";
   String CONTROLLER_CLUSTER_PORT = "io.hyperfoil.controller.cluster.port";
   String LOG4J2_CONFIGURATION_FILE = "log4j.configurationFile";
}

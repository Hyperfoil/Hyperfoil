package io.hyperfoil.deploy.k8s;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.hyperfoil.api.config.Agent;
import io.hyperfoil.api.deployment.AgentProperties;
import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.deployment.Deployer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This deployer expects Hyperfoil to be deployed as Openshift/Kubernetes pod. In order to create one, run:
 * <code>
 * oc new-project hyperfoil
 * oc apply -f hyperfoil.yaml
 * </code>
 * <p>
 * If you want to use custom logging settings, create a configmap:
 * <code>
 * oc create cm log4j2 --from-file=log4j2-trace.xml=/path/to/log4j2-trace.xml
 * </code>
 * <p>
 * This can be referenced as <code>log: log4j2/log4j2-trace.xml</code> in the agent properties.
 * You can also mount the configmap to controller.
 */
public class K8sDeployer implements Deployer {
   private static final Logger log = LoggerFactory.getLogger(K8sDeployer.class);
   private static final String API_SERVER = System.getProperty("io.hyperfoil.deployer.k8s.apiserver", "https://kubernetes.default.svc.cluster.local/");
   private static final String IMAGE = System.getProperty("io.hyperfoil.deployer.k8s.image", "quay.io/hyperfoil/hyperfoil:latest");
   private static final String TOKEN;
   private static final String NAMESPACE;

   private KubernetesClient client;

   static {
      TOKEN = getPropertyOrLoad("io.hyperfoil.deployer.k8s.token", "token");
      NAMESPACE = getPropertyOrLoad("io.hyperfoil.deployer.k8s.namespace", "namespace");
   }

   private static String getPropertyOrLoad(String property, String file) {
      String value = System.getProperty(property);
      if (value != null) {
         return value;
      }
      String path = "/var/run/secrets/kubernetes.io/serviceaccount/" + file;
      try {
         return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
      } catch (IOException e) {
         log.debug("Cannot load {} - not running as pod?", e, path);
         return "<cannot load>";
      }
   }

   private void ensureClient() {
      synchronized (this) {
         if (client == null) {
            Config config = new ConfigBuilder()
                  .withMasterUrl(API_SERVER)
                  .withTrustCerts(true)
                  .withOauthToken(TOKEN)
                  .build();
            client = new DefaultKubernetesClient(config);
         }
      }
   }

   @Override
   public DeployedAgent start(Agent agent, String runId, Consumer<Throwable> exceptionHandler) {
      ensureClient();

      PodSpecBuilder spec = new PodSpecBuilder();
      List<String> command = new ArrayList<>();
      command.add("java");
      ContainerBuilder containerBuilder = new ContainerBuilder()
            .withImage(IMAGE)
            .withName("hyperfoil-agent")
            .withPorts(new ContainerPort(7800, null, null, "jgroups", "TCP"));

      String node = agent.properties.get("node");
      if (node != null) {
         Map<String, String> nodeSelector = new HashMap<>();
         for (String label : node.split(",", 0)) {
            label = label.trim();
            if (label.isEmpty()) {
               continue;
            } else if (label.contains("=")) {
               String[] parts = node.split("=", 2);
               nodeSelector.put(parts[0].trim(), parts[1].trim());
            } else {
               nodeSelector.put("kubernetes.io/hostname", label);
            }
         }
         spec = spec.withNodeSelector(nodeSelector);
      }

      String log = agent.properties.get("log");
      if (log != null) {
         String configMap = log;
         String file = "log4j2.xml";
         if (log.contains("/")) {
            int index = log.indexOf("/");
            configMap = log.substring(0, index);
            file = log.substring(index + 1);
         }
         command.add("-D" + AgentProperties.LOG4J2_CONFIGURATION_FILE + "=file:///etc/log4j2/" + file);

         containerBuilder.withVolumeMounts(new VolumeMountBuilder()
               .withName("log")
               .withMountPath("/etc/log4j2")
               .withNewReadOnly(true)
               .build());
         spec.withVolumes(new VolumeBuilder()
               .withName("log")
               .withConfigMap(new ConfigMapVolumeSource(null, null, configMap, false))
               .build());
      }


      command.add("-Djava.net.preferIPv4Stack=true");
      command.add("-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory");
      command.add("-D" + AgentProperties.AGENT_NAME + "=" + agent.name);
      command.add("-D" + AgentProperties.RUN_ID + "=" + runId);
      command.add("-D" + AgentProperties.CONTROLLER_CLUSTER_IP + "=" + System.getProperty(AgentProperties.CONTROLLER_CLUSTER_IP));
      command.add("-D" + AgentProperties.CONTROLLER_CLUSTER_PORT + "=" + System.getProperty(AgentProperties.CONTROLLER_CLUSTER_PORT));
      if (agent.properties.containsKey("extras")) {
         command.addAll(Arrays.asList(agent.properties.get("extras").split(" ", 0)));
      }
      command.add("-cp");
      command.add("/deployment/lib/*");
      command.add("io.hyperfoil.Hyperfoil$Agent");

      containerBuilder = containerBuilder.withCommand(command);
      spec = spec.withContainers(Collections.singletonList(containerBuilder.build()));

      // @formatter:off
      Pod pod = client.pods().inNamespace(NAMESPACE).createNew()
            .withNewMetadata()
               .withNamespace(NAMESPACE)
               .withName("agent-" + runId.toLowerCase() + "-" + agent.name.toLowerCase())
            .endMetadata()
            .withSpec(spec.build()).done();
      // @formatter:on

      // Keep the agent running after benchmark, e.g. to inspect logs
      boolean stop = !"false".equalsIgnoreCase(agent.properties.getOrDefault("stop", "true"));

      return new K8sAgent(client, pod, stop);
   }

   @Override
   public void downloadAgentLog(DeployedAgent deployedAgent, long offset, String destinationFile, Handler<AsyncResult<Void>> handler) {
      ensureClient();
      K8sAgent agent = (K8sAgent) deployedAgent;
      try (LogWatch log = client.pods().inNamespace(NAMESPACE).withName(agent.pod.getMetadata().getName()).watchLog()) {
         InputStream output = log.getOutput();
         byte[] discardBuffer = new byte[8192];
         while (offset > 0) {
            offset -= output.read(discardBuffer, 0, (int) Math.min(offset, discardBuffer.length));
         }
         Files.copy(output, Paths.get(destinationFile), StandardCopyOption.REPLACE_EXISTING);
         handler.handle(Future.succeededFuture());
      } catch (IOException e) {
         handler.handle(Future.failedFuture(e));
      }
   }

   @Override
   public void close() {
      client.close();
   }

   @MetaInfServices(Deployer.Factory.class)
   public static class Factory implements Deployer.Factory {
      @Override
      public String name() {
         return "k8s";
      }

      @Override
      public K8sDeployer create() {
         return new K8sDeployer();
      }
   }
}

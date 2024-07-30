package io.hyperfoil.deploy.k8s;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.internal.PodOperationsImpl;
import io.hyperfoil.api.Version;
import io.hyperfoil.api.config.Agent;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.deployment.Deployer;
import io.hyperfoil.internal.Controller;
import io.hyperfoil.internal.Properties;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import okhttp3.Request;
import okhttp3.Response;

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
   private static final Logger log = LogManager.getLogger(K8sDeployer.class);
   private static final String API_SERVER = Properties.get("io.hyperfoil.deployer.k8s.apiserver",
         "https://kubernetes.default.svc.cluster.local/");
   private static final String DEFAULT_IMAGE = Properties.get("io.hyperfoil.deployer.k8s.defaultimage",
         "quay.io/hyperfoil/hyperfoil:" + Version.VERSION);
   private static final String CONTROLLER_POD_NAME = System.getenv("HOSTNAME");
   private static final String APP;
   private static final String NAMESPACE;
   /**
    * The <a href="https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/">recommended
    * labels for use in Kubernetes</a>.
    */
   private static final String[] K8S_RECOMMENDED_LABELS = {
         "app.kubernetes.io/name",
         "app.kubernetes.io/instance",
         "app.kubernetes.io/version",
         "app.kubernetes.io/component",
         "app.kubernetes.io/part-of",
         "app.kubernetes.io/managed-by",
         "app.kubernetes.io/created-by"
   };
   protected static final String POD_LABEL_PROPERTY_PREFIX = "pod.label.";

   private KubernetesClient client;

   static {
      APP = Properties.get("io.hyperfoil.deployer.k8s.app", null);
      NAMESPACE = getPropertyOrLoad("io.hyperfoil.deployer.k8s.namespace", "namespace");
   }

   private static String getPropertyOrLoad(String property, String file) {
      String value = Properties.get(property, null);
      if (value != null) {
         return value;
      }
      String path = "/var/run/secrets/kubernetes.io/serviceaccount/" + file;
      try {
         return Files.readString(Paths.get(path));
      } catch (IOException e) {
         log.debug("Cannot load {} - not running as pod?", path, e);
         return "<cannot load>";
      }
   }

   private void ensureClient() {
      synchronized (this) {
         if (client == null) {
            Config config = new ConfigBuilder()
                  .withMasterUrl(API_SERVER)
                  .withTrustCerts(true)
                  .build();
            client = new DefaultKubernetesClient(config);
         }
      }
   }

   @Override
   public DeployedAgent start(Agent agent, String runId, Benchmark benchmark, Consumer<Throwable> exceptionHandler) {
      ensureClient();

      PodSpecBuilder spec = new PodSpecBuilder().withRestartPolicy("Never");
      String serviceAccount = agent.properties.getOrDefault("pod-serviceaccount",
            Properties.get("io.hyperfoil.deployer.k8s.pod.service-account", null));
      if (serviceAccount != null) {
         spec.withServiceAccount(serviceAccount);
      }
      List<String> command = new ArrayList<>();
      command.add("java");
      int threads = agent.threads() < 0 ? benchmark.defaultThreads() : agent.threads();
      ResourceRequirements resourceRequirements = new ResourceRequirements();
      Map<String, Quantity> podResourceRequests = new LinkedHashMap<>();
      String cpuRequest = agent.properties.getOrDefault(
            "pod-cpu",
            Properties.get("io.hyperfoil.deployer.k8s.pod.cpu", null));
      if (cpuRequest != null) {
         podResourceRequests.put("cpu", new Quantity(cpuRequest));
      }
      String memoryRequest = agent.properties.getOrDefault(
            "pod-memory",
            Properties.get("io.hyperfoil.deployer.k8s.pod.memory", null));
      if (memoryRequest != null) {
         podResourceRequests.put("memory", new Quantity(memoryRequest));
      }
      String storageRequest = agent.properties.getOrDefault(
            "pod-ephemeral-storage",
            Properties.get("io.hyperfoil.deployer.k8s.pod.ephemeralstorage", null));
      if (storageRequest != null) {
         podResourceRequests.put("ephemeral-storage", new Quantity(storageRequest));
      }
      resourceRequirements.setRequests(podResourceRequests);
      if (Boolean.parseBoolean(agent.properties.getOrDefault("pod-limits",
            Properties.get("io.hyperfoil.deployer.k8s.pod.limits", "false")))) {
         resourceRequirements.setLimits(podResourceRequests);
      }
      ContainerBuilder containerBuilder = new ContainerBuilder()
            .withImage(agent.properties.getOrDefault("image", DEFAULT_IMAGE))
            .withImagePullPolicy(agent.properties.getOrDefault("imagePullPolicy", "Always"))
            .withName("hyperfoil-agent")
            .withPorts(new ContainerPort(7800, null, null, "jgroups", "TCP"))
            .withNewResourcesLike(resourceRequirements)
            .endResources();

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
         // Tolerate any taints if the node is set explicitly
         spec = spec.withTolerations(new Toleration("", "", "Exists", null, null));
      }

      String logProperty = agent.properties.get("log");
      if (logProperty != null) {
         String configMap = logProperty;
         String file = "log4j2.xml";
         if (logProperty.contains("/")) {
            int index = logProperty.indexOf("/");
            configMap = logProperty.substring(0, index);
            file = logProperty.substring(index + 1);
         }
         command.add("-D" + Properties.LOG4J2_CONFIGURATION_FILE + "=file:///etc/log4j2/" + file);

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

      command.add("-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory");
      command.add("-D" + Properties.AGENT_NAME + "=" + agent.name);
      command.add("-D" + Properties.RUN_ID + "=" + runId);
      command.add("-D" + Properties.CONTROLLER_CLUSTER_IP + "=" + Properties.get(Properties.CONTROLLER_CLUSTER_IP, null));
      command.add("-D" + Properties.CONTROLLER_CLUSTER_PORT + "=" + Properties.get(Properties.CONTROLLER_CLUSTER_PORT, null));
      if (agent.properties.containsKey("extras")) {
         command.addAll(Arrays.asList(agent.properties.get("extras").split(" ", 0)));
      }
      command.add("-cp");
      command.add("/deployment/lib/*:/deployment/extensions/*");
      command.add("io.hyperfoil.Hyperfoil$Agent");

      // Keep the agent running after benchmark, e.g. to inspect logs
      boolean stop = !"false".equalsIgnoreCase(agent.properties.getOrDefault("stop", "true"));

      if (!stop) {
         command.add("&&");
         command.add("sleep");
         command.add("86400");
      }

      containerBuilder = containerBuilder.withCommand(command);
      spec = spec.withContainers(Collections.singletonList(containerBuilder.build()));

      String podName = "agent-" + runId.toLowerCase() + "-" + agent.name.toLowerCase();

      boolean fetchLogs = !"false".equalsIgnoreCase(agent.properties.getOrDefault("fetchLogs", "true"));
      Path outputPath = null;
      FileOutputStream output = null;
      if (fetchLogs) {
         // We're adding the finalizer to prevent destroying the pod completely before we finish reading logs.
         outputPath = Controller.RUN_DIR.resolve(runId).resolve(podName + ".log");
         try {
            output = new FileOutputStream(outputPath.toFile());
         } catch (FileNotFoundException e) {
            log.error("Cannot write to {}", outputPath, e);
         }
         // We cannot start reading the logs right away because we'd only read an error message
         // about the container being started - we'll defer it until all containers become ready.
      }

      Map<String, String> labels = new HashMap<>();
      boolean usingRecommendedLabels = false;
      for (String key : K8S_RECOMMENDED_LABELS) {
         var slashIndex = key.indexOf('/');
         var value = Properties.get("io.hyperfoil.deployer.k8s.label." + (key.substring(slashIndex + 1)), null);
         if (value != null) {
            usingRecommendedLabels = true;
            labels.put(key, value);
         }
      }
      if (usingRecommendedLabels) {
         labels.putIfAbsent("app.kubernetes.io/name", "hyperfoil");
         labels.putIfAbsent("app.kubernetes.io/version", Version.VERSION);
         labels.putIfAbsent("app.kubernetes.io/component", "agent");
         labels.putIfAbsent("app.kubernetes.io/managed-by", "hyperfoil");
         labels.putIfAbsent("app.kubernetes.io/created-by", "hyperfoil");
      } else {
         labels.put("role", "agent");
         if (APP != null) {
            labels.put("app", APP);
         }
      }
      agent.properties.forEach((k, v) -> {
         if (k.startsWith(POD_LABEL_PROPERTY_PREFIX)) {
            labels.put(k.substring(POD_LABEL_PROPERTY_PREFIX.length()), v);
         }
      });
      // @formatter:off
      Pod pod = client.pods().inNamespace(NAMESPACE).createNew()
            .withNewMetadata()
               .withNamespace(NAMESPACE)
               .withName(podName)
               .withLabels(labels)
            .endMetadata()
            .withSpec(spec.build()).done();
      // @formatter:on

      K8sAgent k8sAgent = new K8sAgent(agent, client, pod, stop, outputPath, output);
      if (output != null) {
         client.pods().inNamespace(NAMESPACE).withName(podName).watch(new AgentWatcher(podName, k8sAgent));
      }
      return k8sAgent;
   }

   @Override
   public boolean hasControllerLog() {
      return true;
   }

   @Override
   public void downloadControllerLog(long offset, String destinationFile, Handler<AsyncResult<Void>> handler) {
      downloadRunningLog(CONTROLLER_POD_NAME, offset, destinationFile, handler);
   }

   @Override
   public void downloadAgentLog(DeployedAgent deployedAgent, long offset, String destinationFile,
         Handler<AsyncResult<Void>> handler) {
      K8sAgent agent = (K8sAgent) deployedAgent;
      ensureClient();
      if (agent.outputPath != null) {
         try (InputStream stream = new FileInputStream(agent.outputPath.toFile())) {
            skipBytes(offset, stream);
            Files.copy(stream, Paths.get(destinationFile), StandardCopyOption.REPLACE_EXISTING);
            handler.handle(Future.succeededFuture());
         } catch (IOException e) {
            handler.handle(Future.failedFuture(e));
         }
      } else {
         downloadRunningLog(agent.pod.getMetadata().getName(), offset, destinationFile, handler);
      }
   }

   private void skipBytes(long offset, InputStream stream) throws IOException {
      while (offset > 0) {
         long skipped = stream.skip(offset);
         if (skipped == 0) {
            break;
         }
         offset -= skipped;
      }
   }

   private void downloadRunningLog(String podName, long offset, String destinationFile, Handler<AsyncResult<Void>> handler) {
      ensureClient();
      try {
         PodResource<Pod, DoneablePod> podResource = client.pods().inNamespace(NAMESPACE).withName(podName);
         InputStream stream = getLog(podResource);
         skipBytes(offset, stream);
         Files.copy(stream, Paths.get(destinationFile), StandardCopyOption.REPLACE_EXISTING);
         handler.handle(Future.succeededFuture());
      } catch (IOException e) {
         handler.handle(Future.failedFuture(e));
      }
   }

   private InputStream getLog(PodResource<Pod, DoneablePod> podResource) throws IOException {
      PodOperationsImpl impl = (PodOperationsImpl) podResource;
      URL url = new URL(impl.getResourceUrl().toString() + "/log");
      Request.Builder requestBuilder = new Request.Builder().get().url(url);
      Request request = requestBuilder.build();
      Response response = ((HttpClientAware) client).getHttpClient().newCall(request).execute();
      return response.body().byteStream();
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

   private class AgentWatcher implements Watcher<Pod> {
      private final String podName;
      private final K8sAgent agent;

      AgentWatcher(String podName, K8sAgent agent) {
         this.podName = podName;
         this.agent = agent;
      }

      @Override
      public void eventReceived(Action action, Pod resource) {
         if (resource.getStatus().getConditions().stream()
               .filter(c -> "Ready".equalsIgnoreCase(c.getType()))
               .anyMatch(c -> "True".equalsIgnoreCase(c.getStatus()))) {
            if (agent.logWatch != null) {
               return;
            }
            agent.logWatch = client.pods().inNamespace(NAMESPACE).withName(podName).watchLog(agent.output);
         }
      }

      @Override
      public void onClose(KubernetesClientException cause) {
      }
   }
}

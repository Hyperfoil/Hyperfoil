package io.hyperfoil.deploy.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.hyperfoil.api.Version;
import io.hyperfoil.api.config.Agent;
import io.hyperfoil.internal.Properties;

/**
 * Covers the pod spec {@link K8sDeployer} submits, against an in-process mock API server running in CRUD mode.
 * <p>
 * <b>Scope:</b> this verifies what the deployer <i>asks</i> Kubernetes for. The mock server stores objects and
 * hands them back; it does not schedule pods, start JVMs or carry a single JGroups message. Clustering behaviour
 * (node departure, registration purging) is covered in {@code hyperfoil-clustering} against real channels -
 * it is not Kubernetes-specific and gains nothing from being exercised through an API server.
 */
public class K8sDeployerTest {
   private static final String NAMESPACE_PROPERTY = "io.hyperfoil.deployer.k8s.namespace";
   /** Must match the value surefire sets in {@code pom.xml}; {@link #startServer()} asserts that it does. */
   private static final String NAMESPACE = "hyperfoil-test";

   /** Pod names are derived from the run id, and the CRUD mock rejects duplicates with 409 like a real cluster. */
   private static final AtomicInteger RUN_IDS = new AtomicInteger();

   private static KubernetesMockServer server;
   private static KubernetesClient client;

   private final Map<String, String> setProperties = new HashMap<>();

   @BeforeAll
   static void startServer() {
      assertThat(System.getProperty(NAMESPACE_PROPERTY))
            .as("-D%s must be set before the JVM starts; surefire does this, an IDE run configuration may not",
                  NAMESPACE_PROPERTY)
            .isEqualTo(NAMESPACE);

      // The CRUD dispatcher stores what is POSTed and serves it back, so the pod can be read from the API rather
      // than from the object the deployer happened to return. Plain HTTP: there is no certificate to trust.
      server = new KubernetesMockServer(new Context(), new MockWebServer(), new HashMap<>(),
            new KubernetesCrudDispatcher(), false);
      server.init();
      client = server.createClient();
   }

   @AfterAll
   static void stopServer() {
      client.close();
      server.destroy();
   }

   @AfterEach
   void clearProperties() {
      setProperties.keySet().forEach(System::clearProperty);
      setProperties.clear();
   }

   /**
    * Regression: each comma-separated label must be split on its own '=', not on the first '=' of the whole
    * property. Splitting the whole string made every label after the first collapse into one bogus selector.
    */
   @Test
   void shouldPinToANodeByMultipleLabels() {
      Pod pod = deploy(agent("a", "node", "disk=ssd, zone=eu-west-1a"));

      assertThat(pod.getSpec().getNodeSelector())
            .containsEntry("disk", "ssd")
            .containsEntry("zone", "eu-west-1a")
            .hasSize(2);
   }

   /**
    * Regression: kubernetes-client selects an {@code HttpClient.Factory} off the classpath, and the
    * kubernetes-httpclient-vertx implementation it pulls in by default is built against Vert.x 4 - on the Vert.x 5
    * that Hyperfoil pins, building a client dies with {@code NoSuchMethodError: WebClientOptions.setMaxPoolSize}.
    * <p>
    * Reintroducing the vertx implementation takes this whole class down, because the mock server resolves the
    * same factory when it builds the injected client. What this test adds on top is coverage of the production
    * path itself - {@link K8sDeployer#ensureClient()} with its own {@code Config}, the very first thing
    * {@code start} does - rather than the client the mock server hands out.
    */
   @Test
   void shouldBuildAClientFromTheAmbientConfiguration() {
      K8sDeployer deployer = new K8sDeployer();

      // Builds the client only; kubernetes-client connects lazily, so no API server has to exist.
      assertThatCode(deployer::ensureClient).doesNotThrowAnyException();
      deployer.close();
   }

   @Test
   void shouldNameAndNamespaceThePodFromRunIdAndAgentName() {
      Pod pod = deploy(agent("Agent-One"), "A1B2");

      assertThat(pod.getMetadata().getName()).isEqualTo("agent-a1b2-agent-one");
      assertThat(pod.getMetadata().getNamespace()).isEqualTo(NAMESPACE);
      assertThat(pod.getSpec().getRestartPolicy()).isEqualTo("Never");
   }

   @Test
   void shouldLabelAgentPodsByRoleByDefault() {
      Pod pod = deploy(agent("a"));

      assertThat(pod.getMetadata().getLabels()).containsEntry("role", "agent");
   }

   @Test
   void shouldUseRecommendedLabelsWhenAnyIsConfigured() {
      property("io.hyperfoil.deployer.k8s.label.part-of", "my-suite");

      Pod pod = deploy(agent("a"));

      assertThat(pod.getMetadata().getLabels())
            .containsEntry("app.kubernetes.io/part-of", "my-suite")
            .containsEntry("app.kubernetes.io/name", "hyperfoil")
            .containsEntry("app.kubernetes.io/component", "agent")
            .containsEntry("app.kubernetes.io/managed-by", "hyperfoil")
            .containsEntry("app.kubernetes.io/version", Version.VERSION)
            .doesNotContainKey("role");
   }

   @Test
   void shouldCopyPodLabelPropertiesOntoTheMetadata() {
      Pod pod = deploy(agent("a", "pod.label.team", "perf", "pod.label.tier", "backend"));

      assertThat(pod.getMetadata().getLabels())
            .containsEntry("team", "perf")
            .containsEntry("tier", "backend");
   }

   @Test
   void shouldExposeTheJgroupsPort() {
      ContainerPort port = container(deploy(agent("a"))).getPorts().get(0);

      assertThat(port.getContainerPort()).isEqualTo(7800);
      assertThat(port.getName()).isEqualTo("jgroups");
      assertThat(port.getProtocol()).isEqualTo("TCP");
   }

   @Test
   void shouldPassTheControllerClusterAddressToTheAgent() {
      property(Properties.CONTROLLER_CLUSTER_IP, "10.128.0.17");
      property(Properties.CONTROLLER_CLUSTER_PORT, "7801");

      assertThat(command(deploy(agent("a"))))
            .contains("-D" + Properties.CONTROLLER_CLUSTER_IP + "=10.128.0.17")
            .contains("-D" + Properties.CONTROLLER_CLUSTER_PORT + "=7801");
   }

   @Test
   void shouldPassTheAgentNameAndRunIdToTheAgent() {
      assertThat(command(deploy(agent("worker-3"), "XYZ")))
            .contains("-D" + Properties.AGENT_NAME + "=worker-3")
            .contains("-D" + Properties.RUN_ID + "=XYZ");
   }

   @Test
   void shouldLaunchTheAgentMainClassOffTheDeploymentClasspath() {
      assertThat(command(deploy(agent("a"))))
            .startsWith("java")
            .endsWith("io.hyperfoil.Hyperfoil$Agent")
            .containsSequence("-cp", "/deployment/lib/*:/deployment/extensions/*");
   }

   @Test
   void shouldDefaultToTheReleaseImageAndAlwaysPull() {
      Container container = container(deploy(agent("a")));

      assertThat(container.getName()).isEqualTo("hyperfoil-agent");
      assertThat(container.getImage()).isEqualTo("quay.io/hyperfoil/hyperfoil:" + Version.VERSION);
      assertThat(container.getImagePullPolicy()).isEqualTo("Always");
   }

   @Test
   void shouldLetTheAgentOverrideImageAndPullPolicy() {
      Container container = container(
            deploy(agent("a", "image", "example.com/custom:1.2", "imagePullPolicy", "IfNotPresent")));

      assertThat(container.getImage()).isEqualTo("example.com/custom:1.2");
      assertThat(container.getImagePullPolicy()).isEqualTo("IfNotPresent");
   }

   @Test
   void shouldAppendExtraJvmArgumentsBeforeTheClasspath() {
      assertThat(command(deploy(agent("a", "extras", "-Xmx2g -XX:+UseZGC"))))
            .containsSequence("-Xmx2g", "-XX:+UseZGC", "-cp");
   }

   @Test
   void shouldKeepThePodAliveWhenStopIsDisabled() {
      assertThat(command(deploy(agent("a", "stop", "false"))))
            .containsSequence("&&", "sleep", "86400");
      assertThat(command(deploy(agent("a"))))
            .doesNotContain("sleep");
   }

   @Test
   void shouldRequestResourcesWithoutLimitingThemByDefault() {
      Container container = container(deploy(
            agent("a", "pod-cpu", "2", "pod-memory", "4Gi", "pod-ephemeral-storage", "1Gi")));

      assertThat(container.getResources().getRequests())
            .hasSize(3)
            .containsKeys("cpu", "memory", "ephemeral-storage");
      assertThat(container.getResources().getRequests().get("cpu").getAmount()).isEqualTo("2");
      // Load generators are latency-sensitive: a CPU limit would let the kernel throttle them mid-benchmark.
      assertThat(container.getResources().getLimits()).isNullOrEmpty();
   }

   @Test
   void shouldMirrorRequestsIntoLimitsWhenAsked() {
      Container container = container(deploy(agent("a", "pod-cpu", "2", "pod-limits", "true")));

      assertThat(container.getResources().getLimits()).containsOnlyKeys("cpu");
      assertThat(container.getResources().getLimits().get("cpu").getAmount()).isEqualTo("2");
   }

   @Test
   void shouldPinToANodeByHostname() {
      Pod pod = deploy(agent("a", "node", "worker-7"));

      assertThat(pod.getSpec().getNodeSelector()).containsExactly(Map.entry("kubernetes.io/hostname", "worker-7"));
      // An explicitly chosen node is tolerated whatever taints it carries.
      assertThat(pod.getSpec().getTolerations()).singleElement()
            .satisfies(toleration -> assertThat(toleration.getOperator()).isEqualTo("Exists"));
   }

   /**
    * Two traps here. {@code split(",", 0)} already discards trailing empty strings, so {@code "worker-7,,"} would
    * pass whether or not the deployer skips anything; the blank has to sit between two real entries. And the
    * entries have to be labels rather than hostnames - a skipped blank would otherwise be indistinguishable from
    * a {@code kubernetes.io/hostname} entry that a later hostname overwrites.
    */
   @Test
   void shouldIgnoreEmptyEntriesInTheNodeSelector() {
      Pod pod = deploy(agent("a", "node", " , disk=ssd, , zone=eu-west-1a,"));

      assertThat(pod.getSpec().getNodeSelector())
            .containsEntry("disk", "ssd")
            .containsEntry("zone", "eu-west-1a")
            .hasSize(2);
   }

   @Test
   void shouldNotConstrainSchedulingWhenNoNodeIsGiven() {
      Pod pod = deploy(agent("a"));

      assertThat(pod.getSpec().getNodeSelector()).isNullOrEmpty();
      assertThat(pod.getSpec().getTolerations()).isNullOrEmpty();
   }

   @Test
   void shouldMountALogConfigMapAndPointLog4jAtIt() {
      Pod pod = deploy(agent("a", "log", "my-logging/custom.xml"));

      assertThat(command(pod)).contains("-D" + Properties.LOG4J2_CONFIGURATION_FILE + "=file:///etc/log4j2/custom.xml");
      assertThat(container(pod).getVolumeMounts()).singleElement().satisfies(mount -> {
         assertThat(mount.getName()).isEqualTo("log");
         assertThat(mount.getMountPath()).isEqualTo("/etc/log4j2");
         assertThat(mount.getReadOnly()).isTrue();
      });
      assertThat(pod.getSpec().getVolumes()).singleElement().satisfies(volume -> {
         assertThat(volume.getConfigMap().getName()).isEqualTo("my-logging");
         assertThat(volume.getConfigMap().getOptional()).isFalse();
      });
   }

   @Test
   void shouldDefaultTheLogConfigFileNameWhenOnlyAConfigMapIsGiven() {
      Pod pod = deploy(agent("a", "log", "my-logging"));

      assertThat(command(pod)).contains("-D" + Properties.LOG4J2_CONFIGURATION_FILE + "=file:///etc/log4j2/log4j2.xml");
      assertThat(pod.getSpec().getVolumes().get(0).getConfigMap().getName()).isEqualTo("my-logging");
   }

   @Test
   void shouldSetTheServiceAccountWhenConfigured() {
      Pod pod = deploy(agent("a", "pod-serviceaccount", "hyperfoil-agent-sa"));

      assertThat(pod.getSpec().getServiceAccount()).isEqualTo("hyperfoil-agent-sa");
   }

   /**
    * Runs the deployer against the mock API server and returns the pod it created.
    * <p>
    * {@code fetchLogs=false} keeps the deployer from opening a log file and registering a watch - neither is
    * part of the spec under test, and the watch would outlive the test.
    * <p>
    * {@code benchmark} is null, which is safe only because {@code start} reads it in one place -
    * {@code agent.threads() < 0 ? benchmark.defaultThreads() : ...} - and {@link Agent#threads()} returns 0 when
    * the property is absent. A NullPointerException here means either that guard changed or a test started
    * setting a negative {@code threads}, and this helper needs a real benchmark.
    * <p>
    * The deployer is deliberately not closed: {@link K8sDeployer#close()} would close the shared
    * {@link #client} and break every later test.
    */
   private Pod deploy(Agent agent) {
      return deploy(agent, "r" + RUN_IDS.incrementAndGet());
   }

   private Pod deploy(Agent agent, String runId) {
      agent.properties.putIfAbsent("fetchLogs", "false");
      K8sDeployer deployer = new K8sDeployer();
      deployer.setClient(client);
      K8sAgent deployed = (K8sAgent) deployer.start(agent, runId, null, error -> {
         throw new AssertionError("Deployment reported an error", error);
      });
      return deployed.pod;
   }

   private static Agent agent(String name, String... properties) {
      Map<String, String> map = new HashMap<>();
      for (int i = 0; i < properties.length; i += 2) {
         map.put(properties[i], properties[i + 1]);
      }
      return new Agent(name, null, map);
   }

   private static Container container(Pod pod) {
      assertThat(pod.getSpec().getContainers()).hasSize(1);
      return pod.getSpec().getContainers().get(0);
   }

   private static List<String> command(Pod pod) {
      return container(pod).getCommand();
   }

   /** Sets a system property for the duration of one test; {@link #clearProperties()} undoes it. */
   private void property(String key, String value) {
      setProperties.put(key, value);
      System.setProperty(key, value);
   }
}

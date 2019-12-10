package io.hyperfoil.deploy.k8s;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.hyperfoil.api.config.Agent;
import io.hyperfoil.api.deployment.DeployedAgent;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class K8sAgent implements DeployedAgent {
   private static final Logger log = LoggerFactory.getLogger(K8sAgent.class);

   final Agent def;
   final KubernetesClient client;
   final Pod pod;
   final boolean stop;
   final Path outputPath;
   final FileOutputStream output;
   LogWatch logWatch;

   public K8sAgent(Agent def, KubernetesClient client, Pod pod, boolean stop, Path outputPath, FileOutputStream output) {
      this.def = def;
      this.client = client;
      this.pod = pod;
      this.stop = stop;
      this.outputPath = outputPath;
      this.output = output;
   }

   @Override
   public void stop() {
      if (stop) {
         client.pods().inNamespace(pod.getMetadata().getNamespace()).delete(pod);
      }
      if (logWatch != null) {
         logWatch.close();
      }
      if (output != null) {
         try {
            output.close();
         } catch (IOException e) {
            log.error("Failed to close log output.", e);
         }
      }
   }
}

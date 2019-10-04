package io.hyperfoil.deploy.k8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.hyperfoil.api.deployment.DeployedAgent;

public class K8sAgent implements DeployedAgent {
   private final KubernetesClient client;
   final Pod pod;

   public K8sAgent(KubernetesClient client, Pod pod) {
      this.client = client;
      this.pod = pod;
   }

   @Override
   public void stop() {
      client.pods().inNamespace(pod.getMetadata().getNamespace()).delete(pod);
   }
}

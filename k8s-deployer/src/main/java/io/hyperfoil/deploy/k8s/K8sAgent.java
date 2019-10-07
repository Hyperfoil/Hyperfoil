package io.hyperfoil.deploy.k8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.hyperfoil.api.deployment.DeployedAgent;

class K8sAgent implements DeployedAgent {
   final KubernetesClient client;
   final Pod pod;
   final boolean stop;

   public K8sAgent(KubernetesClient client, Pod pod, boolean stop) {
      this.client = client;
      this.pod = pod;
      this.stop = stop;
   }

   @Override
   public void stop() {
      if (stop) {
         client.pods().inNamespace(pod.getMetadata().getNamespace()).delete(pod);
      }
   }
}

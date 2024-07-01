---
title: Manual k8s/Openshift deployment
description: Manually deploy Hyperfoil in Kubernetes or Openshift environment
categories: [Guide, Installation]
tags: [guides, installation, manual, kubernetes, openshift]
weight: 3
---

If you cannot [use the operator](/docs/user-guide/installation/k8s/) or if you're running vanilla Kubernetes you can define all the resource manually. You deploy only the controller; each agent is then started, when the run starts, as a `pod` in the same namespace and stopped when the run completes.

Following steps install Hyperfoil controller in Openshift, assuming that you have all the required priviledges. With vanilla Kubernetes you might have to replace the `route` with an appropriate `ingress`.

#### 1. Create new namespace for hyperfoil

```sh
oc new-project hyperfoil
```

#### 2. Create required resources

```sh
curl -s -L k8s.hyperfoil.io | oc apply -f -
```
```sh
role.rbac.authorization.k8s.io/controller created
serviceaccount/controller created
service/hyperfoil created
rolebinding.rbac.authorization.k8s.io/controller created
deploymentconfig.apps.openshift.io/controller created
route.route.openshift.io/hyperfoil created
```

The route will use hostname following the format `hyperfoil-hyperfoil.apps.my.cluster.domain` - feel free to customize the hostname as needed.

#### 3. Wait until the image gets downloaded and the container starts

```sh
oc get po
```
```sh
NAME                  READY   STATUS              RESTARTS   AGE
controller-1-pqbvs    1/1     Running             0          57s
controller-1-deploy   0/1     Completed           0          72s
```

#### 4. Open CLI and connect to the controller

While default Hyperfoil port is 8090, Openshift router will expose the service on port 80.

```sh
bin/cli.sh
```
```sh
[hyperfoil]$ connect hyperfoil-hyperfoil.apps.my.cluster.domain -p 80
Connected!
WARNING: Server time seems to be off by 12124 ms
```

#### 5. Upload and run benchmarks as usual

We're using `k8s-hello-world.hf.yaml` in this example.

{{< readfile file="/static/benchmarks/k8s-hello-world.hf.yaml" code="true" lang="yaml" >}}

Note that it can take several seconds to spin up containers with agents.

```sh
[hyperfoil@hyperfoil-hyperfoil]$ upload .../k8s-hello-world.hf.yaml
Loaded benchmark k8s-hello-world, uploading...
... done.

[hyperfoil@hyperfoil-hyperfoil]$ run k8s-hello-world
Started run 0000
Run 0000, benchmark k8s-hello-world
Agents: agent-one[STARTING]
Started: 2019/11/18 19:07:36.752    Terminated: 2019/11/18 19:07:41.778
NAME  STATUS      STARTED       REMAINING  COMPLETED     TOTAL DURATION               DESCRIPTION
main  TERMINATED  19:07:36.753             19:07:41.778  5025 ms (exceeded by 25 ms)  5.00 users per second

[hyperfoil@hyperfoil-hyperfoil]$
```

You can find more details about adjusting the agents in the [benchmark format reference](/docs/user-guide/benchmark/agent/#kubernetesopenshift-deployer).

Running Hyperfoil inside the cluster you are trying to test might skew results due to different network topology compared to driving the load from 'outside' (as real users would do). It is your responsibility to validate if your setup and separation between load driver and SUT (system under test) is correct. You have been warned.

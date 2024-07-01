---
title: Ansible startup
description: Deploy Hyperfoil using Ansible Galaxy scripts
categories: [Guide, Installation]
tags: [guides, installation, ansible]
weight: 4
---

You can fetch release, distribute and start the cluster using Ansible Galaxy scripts; [setup](https://github.com/Hyperfoil/hyperfoil_setup), [test](https://github.com/Hyperfoil/hyperfoil_test), [shutdown](https://github.com/Hyperfoil/hyperfoil_shutdown)

First, get the scripts:
```sh
ansible-galaxy install hyperfoil.hyperfoil_setup,{{ site.last_release.galaxy_version }}
ansible-galaxy install hyperfoil.hyperfoil_shutdown,{{ site.last_release.galaxy_version }}
ansible-galaxy install hyperfoil.hyperfoil_test,{{ site.last_release.galaxy_version }}
```

Now, edit your `hosts` file, it could look like this:
```sh
[hyperfoil-controller]
controller ansible_host=localhost

[hyperfoil-agent]
agent-1 ansible_host=localhost
```
{{% alert title="Note" color="primary" %}}
You can add more agents by duplicating the last line with `agent-2` etc.
{{% /alert %}}

Prepare your playbook; here is a short example that starts the controller, uploads and starts [simple benchmark](https://github.com/Hyperfoil/hyperfoil_test/blob/master/benchmarks/example.yaml.j2) (the templating engine replaces the agents in benchmark script based on Ansible hosts) and waits for its completion. When it confirms number of requests executed it stops the controller.

```yaml
- hosts: [ hyperfoil-agent, hyperfoil-controller ]
  tasks: [] # This will only gather facts about all nodes
- hosts: hyperfoil-controller
  roles:
  - hyperfoil.hyperfoil_setup
- hosts: 127.0.0.1
  connection: local
  roles:
  - hyperfoil.hyperfoil_test
  vars:
    test_name: example
# Note that due to the way Ansible lookups work this will work only if hyperfoil-controller == localhost
- hosts: 127.0.0.1
  connection: local
  tasks:
  - name: Find number of requests
    set_fact:
      test_requests: "{{ lookup('csvfile', 'example file=/tmp/hyperfoil/workspace/run/' + test_runid + '/stats/total.csv col=2 delimiter=,')}}"
  - name: Print number of requests
    debug:
      msg: "Executed {{ test_requests }} requests."
- hosts:
  - hyperfoil-controller
  roles:
  - hyperfoil.hyperfoil_shutdown
```

Finally, run the playbook:
```sh
ansible-playbook -i hosts example.yml
```

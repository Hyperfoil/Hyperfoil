This document summarizes all the steps needed to release next version of Hyperfoil.

It is assumed that you have all the priviledges on Sonatype, GitHub push rights and set up GPG keys.

Start with building and releasing artefacts:

```
$ mvn release:prepare -Prelease
What is the release version for "Hyperfoil"? ... <- Use semantic version (X.Y.Z), default guessed by Maven works.
...
What is SCM release tag or label for "Hyperfoil"? <- Use tag 'release-X.Y.Z'
What is the new development version for "Hyperfoil"? ... <- Use semantic version (X.Y.Z) with -SNAPSHOT suffix
...
$ mvn release:perform -Prelease
```

This creates the tag and pushes everything in the Hyperfoil/Hyperfoil repo. The publication of distribution zip and container image is automatized by [Travis CI](https://www.travis-ci.org/Hyperfoil/Hyperfoil). You can check [GitHub](https://github.com/Hyperfoil/Hyperfoil/releases) and [Quay](https://quay.io/repository/hyperfoil/hyperfoil?tab=tags) that the artifact appeared after few minutes. 

Create tags for Ansible Galaxy scripts:
```
$ cd ../hyperfoil-ansible/hyperfoil_setup
$ git pull && git tag X.Y.Z && git push --tags
$ cd cd ../hyperfoil_test/
$ git pull && git tag X.Y.Z && git push --tags
$ cd cd ../hyperfoil_shutdown/
$ git pull && git tag X.Y.Z && git push --tags
```

Travis CI will notify Ansible Galaxy to publish new version.

Now you have to update documentation on website. Go to you clone of hyperfoil.github.io, drop the generated steps and replace them with the new ones.
```
$ cd ../hyperfoil.github.io
$ rm docs/steps/*
$ cp ../Hyperfoil/distribution/target/steps/* docs/steps/
$ cp ../Hyperfoil/distribution/target/distribution/docs/schema.json schema.json
```

It's recommended to review changes in the generated docs using `git diff` at this point.
In order to properly redirect from http://hyperfoil.io/schema to the JSON file we need to add this Front Matter to the beginning of the file:

```
---
redirect_from: /schema
---
(here follows the actual JSON)
```

Then you need to edit `_config.yml` and update the release tags - check out this section:
```
last_release:
  zip: hyperfoil-X.Y.Z.zip
  dir: hyperfoil-X.Y.Z
  tag: release-X.Y.Z
  galaxy_version: "X.Y.Z"
  url: https://github.com/Hyperfoil/Hyperfoil/releases/download/release-X.Y.Z/hyperfoil-X.Y.Z.zip
```

You can now commit and push the documentation.
```
$ git commit -a -m 'Release X.Y.Z' && git push
```

Last step would be publishing a new version of operator; Major and minor version of operator should match the version of Hyperfoil, micro version can be independent (used for enhancements of the operator itself). It's not necessary to release the operator in sync with main release; publishing it through Marketplace requires a PR with peer review anyway and therefore it lags.

Go to the operator directory and create new Cluster Service Version:
```
$ cd ~/go/src/github.com/Hyperfoil/hyperfoil-operator/
$ export PREVIOUS=X.(Y-1).Z
$ export NEXT=X.Y.Z
$ cp -r deploy/olm-catalog/hyperfoil-operator/$PREVIOUS deploy/olm-catalog/hyperfoil-operator/$NEXT
$ mv deploy/olm-catalog/hyperfoil-operator/$NEXT/hyperfoil-operator.v$PREVIOUS.clusterserviceversion.yaml deploy/olm-catalog/hyperfoil-operator/hyperfoil-operator.v$NEXT.clusterserviceversion.yaml
```

You should edit `deploy/olm-catalog/hyperfoil-operator/$NEXT/hyperfoil-operator.v$NEXT.clusterserviceversion.yaml` and replace all versions appropriatelly (don't forget the `replaces: `, too), and change most recent version in `deploy/olm-catalog/hyperfoil-operator/hyperfoil-bundle.package.yaml`. Also change the default versions in `version/version.go`.

Build the operator and push it to Quay:

```
$ operator-sdk build quay.io/example/memcached-operator:$NEXT
$ docker push quay.io/hyperfoil/hyperfoil-operator:$NEXT
```

Once that you verify that the operator works (you can apply the CRD and CSV manually), you can issue a PR to [Community operators GitHub](https://github.com/operator-framework/community-operators/tree/master/community-operators/hyperfoil).

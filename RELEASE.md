# Hyperfoil Release

This document summarizes all the steps needed to release next version of Hyperfoil.

## Prerequisites

Here the full list of prerequisites and privileges that are required to release a new Hyperfoil version:

1. GitHub push rights on [github.com/Hyperfoil/Hyperfoil](https://github.com/Hyperfoil/Hyperfoil) repository.
2. [Sonatype](https://s01.oss.sonatype.org) access rights.
   1. More details on [central sonatype documentation](https://central.sonatype.org/register/legacy/)
   2. Once you have the account created, someone already in sonatype Hyperfoil org has to add you there.
3. [Optional] [Quay.io/hyperfoil](https://quay.io/organization/hyperfoil) push rights.
   1. This is only required if the automated process that pushes the container image does not work.
   2. More details on this step can be found in the [release process](#release-process).

## Release Process

Below you can find a step-by-step guide to release a new Hyperfoil version.

### Prepare tag and next development cycle

From the `master` branch (ensure it is up-to-date), run the following command:
```bash
mvn release:prepare -Prelease
```

You will be asked to confirm or change the version, something like:
```bash
What is the release version for "Hyperfoil"? ... <- Use semantic version (X.Y.Z), default guessed by Maven works.
...
What is SCM release tag or label for "Hyperfoil"? <- Use tag 'release-X.Y.Z'
What is the new development version for "Hyperfoil"? ... <- Use semantic version (X.Y.Z) with -SNAPSHOT suffix
...
```

This command will create two commits:
1. `[maven-release-plugin] prepare release hyperfoil-all-<VERSION>`: Release version upgrade on all POMs.
2. `[maven-release-plugin] prepare for next development iteration`: Version upgrade to the next `SNAPSHOT` version.

Additionally, the process will create a new tag `hyperfoil-all-<VERSION>` starting from the commit first commit above.
After that, it will push both `master` and the new _tag_.

At the end you should see some un-versioned backup POM files and a release text file, do not remove them as they will
be required by the [Push maven artifacts step](#push-maven-artifacts-to-sonatype).

> **NOTE:** Command `mvn release:prepare` triggers the tests, therefore the process might fail if there is any failure.

### Push maven artifacts to sonatype

To push the maven artifacts you just need to run:
```bash
mvn release:perform -Prelease
```

Once the command finishes, you should see the new artifacts available at [s01.oss.sonatype.org/content/repositories/releases/io/hyperfoil/](https://s01.oss.sonatype.org/content/repositories/releases/io/hyperfoil/)

### [Optional] Push container image to quay.io

This process should be automatically triggered by [workflows/build-and-push-image.yml](https://github.com/Hyperfoil/Hyperfoil/blob/226c7ce15f55d4bbd5964ff6b1855d21c36be91d/.github/workflows/build-and-push-image.yml)
as soon as the previous step pushes the new _tag_ on GitHub.

If, for any reason, the GitHub workflow does not work you could manually follow these steps:

1. Checkout the latest generated tag
2. Build the container image
   ```bash
   mvn clean -B package --file pom.xml -Pbuild-image
   ```
3. Push the container image
   ```bash
   docker push quay.io/hyperfoil/hyperfoil:<VERSION>
   docker push quay.io/hyperfoil/hyperfoil:latest
   ```
   > **NOTE:** To run this command you need to have [quay.io/Hyperfoil](https://quay.io/organization/hyperfoil) push rights
4. Create a new GitHub release
   1. Start drafting the release using [GitHub web UI](https://github.com/Hyperfoil/Hyperfoil/releases/new).
   2. Auto-generate the release note
   3. Attach the Hyperfoil distribution zip file `./distribution/target/hyperfoil-<VERSION>.zip`

## Website Update

Now you have to update documentation on website. 
Clone [hyperfoil.github.io](https://github.com/Hyperfoil/hyperfoil.github.io) repository, drop the generated steps and replace them with the new ones.

```bash
# local clone of the hyperfoil.github.io repository 
cd ../hyperfoil.github.io

# Remove and replace old steps docs with the latest generated steps files
rm docs/steps/*
cp ../Hyperfoil/distribution/target/steps/* docs/steps/

# Update the benchmark JSON schema
cp ../Hyperfoil/distribution/target/distribution/docs/schema.json schema.json
```

It's recommended to review changes in the generated docs using `git diff` at this point.
In order to properly redirect from http://hyperfoil.io/schema to the JSON file we need to add this Front Matter to the 
beginning of the schema.json file:

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
  tag: hyperfoil-all-X.Y.Z
  galaxy_version: "X.Y.Z"
  url: https://github.com/Hyperfoil/Hyperfoil/releases/download/release-X.Y.Z/hyperfoil-X.Y.Z.zip
```

You can now commit and push the documentation (or open a pull request).
```
$ git commit -a -m 'Release X.Y.Z' && git push
```

Note that while the operator synchronizes to the latest Hyperfoil versions on release (we'll probably drop this practice
after 1.0 as it is confusing for some users) we don't have to release the operator for every release of the project.
Therefore, there will be gaps in operator versions, and older version of operator will most likely work with most recent
Hyperfoil.

## Update Ansible Scripts

> **NOTE**: This section refers to an old automation process that might be no more applicable!

### Prerequisites

Clone the following repositories:
- [Hyperfoil/hyperfoil_setup](https://github.com/Hyperfoil/hyperfoil_setup)
- [Hyperfoil/hyperfoil_test](https://github.com/Hyperfoil/hyperfoil_test)
- [Hyperfoil/hyperfoil_shutdown](https://github.com/Hyperfoil/hyperfoil_shutdown)

### Create tags for Ansible Galaxy scripts
```
$ cd ../hyperfoil-ansible/hyperfoil_setup
$ git pull && git tag X.Y.Z && git push --tags
$ cd cd ../hyperfoil_test/
$ git pull && git tag X.Y.Z && git push --tags
$ cd cd ../hyperfoil_shutdown/
$ git pull && git tag X.Y.Z && git push --tags
```

Travis CI will notify Ansible Galaxy to publish new version.
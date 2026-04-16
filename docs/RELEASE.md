# Hyperfoil Release Guide

This document describes the steps needed to release a new version of Hyperfoil.

## Pre-release Checklist

### Report template

The HTML report template lives in the [`Hyperfoil/report`](https://github.com/Hyperfoil/report) repo. If any changes were made there since the last release, someone needs to build the report and copy the output into the main repo before tagging the release:

1. In the `report` repo, run `npm install && npm run build`.
2. Copy the built `report-template.html` to `clustering/src/main/resources/report-template.html` in the main Hyperfoil repo.
3. Commit and push to the stable branch (or include in a PR targeting it).

If no changes were made to the report repo, this step can be skipped.

## Automated Release Process

All credentials (GPG key, Maven Central Portal, SSH deploy key, Quay.io) are stored as GitHub Secrets. No local credentials are needed.

### Minor release (e.g., 0.28 -> 0.29)

A minor release introduces new features and creates a new stable branch.

1. Trigger the **Create New Stable Branch** workflow (`branch.yml`) from `master` via the GitHub Actions UI. This creates the `X.Y.x` branch, bumps master to the next minor SNAPSHOT, and updates workflow references.
2. Trigger the **Perform Release** workflow (`release.yml`) from the newly created `X.Y.x` branch. This handles Maven Central publishing, GPG signing, container image build and push to quay.io, and GitHub Release creation.
3. Submit a PR to the Hyperfoil repo targeting `master` with:
   - `docs/site/content/en/blog/releases/release_notes.md` — add the changelog for the new version
   - `docs/site/hugo.yaml` — update `version`, `github_branch`, `url_latest_distribution`, and `zip_latest_distribution`

   Add the `backport` label to this PR. When merged, the backport workflow will automatically create a cherry-pick PR targeting the `X.Y.x` stable branch. Merge that backport PR as well — the website deploys from the stable branch, not `master`.
4. Push directly to `master` in the [`Hyperfoil/Hyperfoil.github.io`](https://github.com/Hyperfoil/Hyperfoil.github.io) repo, updating `.github/workflows/deploy-gh-pages.yaml` to reference the new stable branch (e.g., `ref: '0.29.x'`). This repo uses direct pushes to `master`, not PRs. Then manually trigger the **Build and deploy Hyperfoil website** workflow from the [Actions tab](https://github.com/Hyperfoil/Hyperfoil.github.io/actions) — it uses `workflow_dispatch` and does not run automatically on push. After it completes, wait for the subsequent **pages build and deployment** workflow to finish before verifying the site.
5. Push directly to `main` in the [`Hyperfoil/jbang-catalog`](https://github.com/Hyperfoil/jbang-catalog) repo: bump all Hyperfoil version references in `jbang-catalog.json` to the new release version (e.g., `0.29.0`). This affects the `wrk`, `wrk2`, `cli`, and `run` aliases.

### Micro/patch release (e.g., 0.28.0 -> 0.28.1)

A micro release applies bug fixes to an existing stable branch. No branch creation is needed.

1. Trigger the **Perform Release** workflow (`release.yml`) from the existing `X.Y.x` branch (e.g., `0.28.x`).
2. Submit a PR targeting the `X.Y.x` stable branch directly, updating `docs/site/hugo.yaml` with the new version and download URL. Update `docs/site/content/en/blog/releases/release_notes.md` with the changelog if needed.
3. Manually trigger the **Build and deploy Hyperfoil website** workflow from the [`Hyperfoil/Hyperfoil.github.io` Actions tab](https://github.com/Hyperfoil/Hyperfoil.github.io/actions) to update the site with the new version. After it completes, wait for the subsequent **pages build and deployment** workflow to finish before verifying the site.
4. Push directly to `main` in the [`Hyperfoil/jbang-catalog`](https://github.com/Hyperfoil/jbang-catalog) repo: bump all Hyperfoil version references in `jbang-catalog.json` to the new release version.

## Manual Release Process (fallback)

If the automated workflows are unavailable, you can release manually.

### Prerequisites

1. GitHub push rights on [github.com/Hyperfoil/Hyperfoil](https://github.com/Hyperfoil/Hyperfoil).
2. [Maven Central Portal](https://central.sonatype.com) access rights. Once you have an account, someone already in the Hyperfoil org on Sonatype has to add you.
3. GPG keys configured locally for signing artifacts.
4. [Optional] [Quay.io/hyperfoil](https://quay.io/organization/hyperfoil) push rights — only needed if the automated container image push fails.

### Build and release artifacts

Run from the **stable branch** (e.g., `0.28.x`), not from `master`:

```bash
$ mvn release:prepare -Prelease
What is the release version for "Hyperfoil"? ... <- Use semantic version (X.Y.Z), default guessed by Maven works.
...
What is SCM release tag or label for "Hyperfoil"? <- Use tag 'release-X.Y.Z'
What is the new development version for "Hyperfoil"? ... <- Use semantic version (X.Y.Z) with -SNAPSHOT suffix
...
$ mvn release:perform -Prelease
```

> [!NOTE]
> `mvn release:prepare` runs tests, so the process may fail if there are test failures.

This creates the tag and pushes everything in the Hyperfoil/Hyperfoil repo. Check [GitHub Releases](https://github.com/Hyperfoil/Hyperfoil/releases) and [Quay](https://quay.io/repository/hyperfoil/hyperfoil?tab=tags) that the artifacts appeared after a few minutes.

### Container image (if not published automatically)

Build and push the multi-arch container image manually:

```bash
$ mvn clean -B package --file pom.xml
$ cd distribution
$ podman build --platform=linux/amd64,linux/arm64 \
    --manifest quay.io/hyperfoil/hyperfoil:X.Y.Z \
    -f src/main/docker/Dockerfile .
$ podman manifest push quay.io/hyperfoil/hyperfoil:X.Y.Z
$ podman manifest push quay.io/hyperfoil/hyperfoil:X.Y.Z quay.io/hyperfoil/hyperfoil:latest
```

> [!NOTE]
> You need [quay.io/hyperfoil](https://quay.io/organization/hyperfoil) push rights to run this.

### GitHub Release (if not created automatically)

Create the release on [GitHub](https://github.com/Hyperfoil/Hyperfoil/releases) manually, attaching the distribution zip `distribution/target/hyperfoil-X.Y.Z.zip`.

## Website Update

The documentation at https://hyperfoil.io is deployed from the latest stable branch via [Hyperfoil/Hyperfoil.github.io](https://github.com/Hyperfoil/Hyperfoil.github.io).

Most changes should already be in place at release time, but verify the following:

1. Generated reference docs (steps, processors, actions) are up to date:

   ```bash
   rm docs/site/content/en/docs/reference/steps/step_*
   rm docs/site/content/en/docs/reference/processors/processor_*
   rm docs/site/content/en/docs/reference/actions/action_*

   cp distribution/target/steps/step_* docs/site/content/en/docs/reference/steps
   cp distribution/target/steps/processor_* docs/site/content/en/docs/reference/processors
   cp distribution/target/steps/action_* docs/site/content/en/docs/reference/actions
   ```

   > [!NOTE]
   > Review changes with `git diff` before committing.

2. JSON schema is up to date:

   ```bash
   cp distribution/target/distribution/docs/schema.json docs/site/static/schema.json
   ```

   Ensure the schema file has this front matter at the top for the redirect to work:

   ```
   ---
   redirect_from: /schema
   ---
   (actual JSON follows)
   ```

3. `hugo.yaml` references the new version (see step 3 of the automated minor release process).

## Hyperfoil Operator

Note that while the operator synchronizes to the latest Hyperfoil versions on release (we'll probably drop this practice
after 1.0 as it is confusing for some users) we don't have to release the operator for every release of the project.
Therefore, there will be gaps in operator versions, and older version of operator will most likely work with most recent
Hyperfoil.

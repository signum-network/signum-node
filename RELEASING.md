# Releasing Signum Node

Releases are driven by a single command and a single source of truth
(`gradle.properties` `version=`). You never hand-edit version strings — the
version is injected at build time into `Signum.VERSION`, the OpenAPI spec, and the
Windows installer.

## TL;DR — cut a release

From an up-to-date, clean `develop`:

```bash
git switch develop
git pull
./gradlew releaseTag
```

Answer the three prompts (bump / hard fork? / notes). The task opens a release PR
into `develop`. Merge it, promote `develop` → `main`, and the tag + GitHub release
are produced automatically.

## Prerequisites

- You are on `develop`, the working tree is clean, and it is in sync with origin.
  (The task refuses to run otherwise.)
- The [`gh` CLI](https://cli.github.com/) is installed and authenticated, so the
  release PR can be opened for you. (If it is missing, the task still pushes the
  branch and prints a link to open the PR manually.)
- One-time, by a repo admin: the `RELEASE_TAG_PAT` secret exists (see
  [One-time setup](#one-time-setup)).

## Step by step

### 1. Run the release command

```bash
./gradlew releaseTag
```

You will be asked for:

- **bump** — `patch` | `minor` | `major`. Computed from the current
  `gradle.properties` version (e.g. `3.9.11` + `minor` → `3.10.0`).
- **hard fork?** — a mandatory-upgrade release. When `yes`, the bump must be at
  least `minor` (a `patch` is rejected) and the changelog/release are marked with
  a mandatory-upgrade warning.
- **release notes** — a single line. You can refine the full notes later by
  editing `CHANGELOG.md` on the release branch before the PR is merged.

What the task does, in order:

1. Pre-flight guards (on `develop`, clean tree, in sync with origin).
2. Full build **and tests** — it stops here if anything fails, before prompting.
3. Creates `release/<version>`, bumps `gradle.properties`, prepends a
   `CHANGELOG.md` section, commits (`release: vX.Y.Z`), pushes the branch, and
   opens a PR into `develop`.

### 2. Review and merge the PR

Review the release PR (refine the CHANGELOG entry on the branch if needed) and
merge it into `develop`.

### 3. Promote `develop` → `main`

Merge `develop` into `main` as usual. This is the only manual git step after the
PR; it is intentionally a human action.

### 4. Automatic tag, build, and release

On push to `main`, the **auto-tag** workflow reads the version from
`gradle.properties` and, if no `vX.Y.Z` tag exists yet, creates and pushes it.
That tag triggers:

- **release.yml** — builds the Windows artifacts and drafts a GitHub release whose
  body is the matching `CHANGELOG.md` section (titled with a hard-fork warning when
  applicable).
- **dockerhub.yml** — builds and pushes the multi-arch Docker images.

### 5. Publish

Review the drafted GitHub release and publish it.

## Non-interactive (CI / scripted)

Provide the answers as properties to skip the prompts:

```bash
./gradlew releaseTag \
  -Prelease.bump=minor \
  -Prelease.hardFork=true \
  -Prelease.notes="What changed in this release"
```

Additional properties:

- `-Prelease.notesFile=<path>` — read multi-line notes from a file.
- `-Prelease.branch=<branch>` — release from a branch other than `develop`.
- `-Prelease.repo=<owner/name>` — repo slug used for the manual-PR link fallback.

## One-time setup

The auto-tag job pushes the tag using the `RELEASE_TAG_PAT` repository secret — a
PAT (fine-grained or classic) with `contents: write` on this repo. This is
**required**: a tag pushed with the default `GITHUB_TOKEN` would not trigger the
downstream `release.yml` / `dockerhub.yml` workflows. Add it under
Settings → Secrets and variables → Actions.

## How the version flows

```
gradle.properties (version=X.Y.Z)   ← single source of truth, bumped by releaseTag
        │  build.gradle computes nodeVersion
        ├── version.properties resource → BuildInfo → Signum.VERSION (runtime)
        ├── OpenAPI info.version (bundled spec)
        └── Windows installer app-version
```

CI builds pass `-PreleaseBuild` so released artifacts always carry a clean
(non-prerelease) version. Local and CI builds that are not on a release tag are
suffixed `-dev` and flagged as prereleases (a `-dev` node refuses to run on
mainnet).

# Releasing Signum Node

Releases are driven by a single command and a single source of truth
(`gradle.properties` `version=`). You never hand-edit version strings.

## Cut a release

From an up-to-date, clean `develop`:

    ./gradlew releaseTag

You will be asked for:

- **bump**: `patch` | `minor` | `major`
- **hard fork?**: a mandatory-upgrade release. If yes, the bump must be at least
  `minor` (a `patch` is rejected).
- **release notes**: one line (refine later in the PR).

The task: runs pre-flight guards (on `develop`, clean tree, in sync) → runs the
full build + tests (it stops here if anything fails) → creates
`release/<version>` → bumps `gradle.properties` → adds a `CHANGELOG.md` section →
commits, pushes, and opens a PR into `develop`.

### Non-interactive (CI / scripted)

    ./gradlew releaseTag -Prelease.bump=minor -Prelease.hardFork=true -Prelease.notes="..."

Other properties: `-Prelease.notesFile=<path>`, `-Prelease.branch=<branch>`,
`-Prelease.repo=<owner/name>`.

## After the PR merges

1. Merge the `release/<version>` PR into `develop` (normal review).
2. Manually promote `develop` → `main` (as today).
3. On push to `main`, **auto-tag.yml** reads the version from `gradle.properties`
   and, if no `vX.Y.Z` tag exists, creates and pushes it.
4. The tag push triggers **release.yml** (Windows artifacts + draft release whose
   body is the matching `CHANGELOG.md` section) and **dockerhub.yml** (images).
5. Review and publish the draft GitHub release.

## One-time setup

`auto-tag.yml` pushes the tag using the `RELEASE_TAG_PAT` repository secret
(a PAT with `contents: write`). This is required so the tag push triggers the
downstream release workflows — a tag pushed with the default `GITHUB_TOKEN`
would not. Add the secret in repo Settings → Secrets and variables → Actions.

## How the version flows

`gradle.properties` → `nodeVersion` (build.gradle) → injected into
`Signum.VERSION` (via `version.properties` resource read by `BuildInfo`), the
OpenAPI JSON, and the Windows installer. CI builds pass `-PreleaseBuild` so
released artifacts always carry a clean (non-`dev`) version; local/dev builds are
suffixed `-dev` and flagged as prereleases.

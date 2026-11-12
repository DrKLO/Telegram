# Telegram Custom – fork infrastructure

A patch-based custom build of [DrKLO/Telegram](https://github.com/DrKLO/Telegram):
Anti-Unsend + Edit History, custom icon, own version numbers, reproducible GitHub Actions builds.
Feature design and limits: [`docs/DESIGN.md`](docs/DESIGN.md).

## What is where

| Path | Purpose |
|---|---|
| `custom/patches/mygram.patch` | Combined MyGram package identity, branding, and custom feature changes |
| `custom/version.properties` | Custom version name/code, patch version, upstream version + commit |
| `custom/CHANGELOG.md` | Source of the release notes (one `## [X.Y.Z]` section per release) |
| `tg-icon.png` (repo root, you provide) | Source of the launcher icon, never modified by any script |
| `scripts/apply-patch.sh` | Apply / check the patch; on conflict writes `build-reports/patch-conflict-report.md` and stops |
| `scripts/regenerate-patch.sh` | Rebuild the patch after you resolved a conflict |
| `scripts/apply-icon.sh` (+ `icon_gen.py`) | Generate and install all icon resources into the build workspace |
| `scripts/apply-buildvars.sh` | Inject **your** `api_id` / `api_hash` (secrets) into `BuildVars.java` |
| `scripts/version.sh` | Read / validate / bump the custom version, print Gradle `-P` overrides |
| `scripts/check-upstream.sh` | Verify (or `--record`) the upstream commit the fork is based on |
| `scripts/sync-upstream.sh` | Merge upstream into branch `upstream-sync`, re-check patch + icon (never publishes) |
| `scripts/verify-apk.sh` | Verify the built APK (signature, version, ABIs, patch present, icon applied) |
| `scripts/gen-release-notes.sh` | Release notes from `CHANGELOG.md` + version file |
| `.github/workflows/build-release.yml` | Validate → build → verify → artifacts → (optional) GitHub Release |
| `.github/workflows/sync-upstream.yml` | Weekly/manual upstream sync, opens a PR |

The custom feature and MyGram package patches are applied **in the CI workspace only**; your fork branch stays upstream source plus patch files, scripts, and workflows.
That is why syncing with upstream never conflicts with your features, and a real conflict shows up as a clear patch report.

## One-time setup

1. Fork `DrKLO/Telegram` on GitHub, then `git clone --recursive <your-fork>`; `git remote add upstream https://github.com/DrKLO/Telegram.git`.
2. Copy `custom/`, `scripts/`, `.github/` into the repository root and add your `tg-icon.png` (square PNG, ≥ 512 px recommended, ≥ 192 px required).
3. Make the scripts executable in git: `git update-index --chmod=+x scripts/*.sh` (the workflow checks this).
4. `scripts/check-upstream.sh --record` – verifies the patch applies and writes the upstream commit into `custom/version.properties`. Commit it.
5. Create the secrets (Settings → Secrets and variables → Actions):

| Secret | Needed for | Notes |
|---|---|---|
| `KEYSTORE_BASE64` | release (required) | `base64 -w0 my.keystore`. Without it only a **test-key** build is possible (the upstream key is public). |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | release (required) | |
| `TG_API_ID`, `TG_API_HASH` | release (required) | From <https://my.telegram.org/apps>. Telegram's README requires your own api_id. |
| `GOOGLE_SERVICES_JSON_BASE64` | release (required) | Base64 of your Firebase config for `org.saimum.messenger` (push notifications). |

   Optional repository **variable** `ICON_REPLACE_ALTERNATES=true` also replaces the six alternative app icons in Settings → Appearance.
6. For the sync PR: Settings → Actions → General → allow Actions to create pull requests.

## Everyday use

* **Build a test APK:** Actions → *Build & Release* → Run workflow (publish unticked). Artifacts: APK, sha256, build-info, logs.
* **Local build:** `scripts/apply-patch.sh && scripts/apply-buildvars.sh && scripts/apply-icon.sh && ./gradlew $(scripts/version.sh gradle-args) :TMessagesProj_App:assembleAfatRelease` (JDK 17, SDK 36, build-tools 36.0.0, NDK 27.2.12479018, CMake 3.22.1).
* **Follow upstream:** the *Sync upstream* workflow (or `scripts/sync-upstream.sh`) merges upstream into `upstream-sync`, verifies the patch and opens a PR. No APK, no release.
* **If the patch conflicts:** download the `patch-conflict-report`, then on a throw-away branch `git apply --3way custom/patches/mygram.patch`, fix the markers, `scripts/regenerate-patch.sh`, `scripts/version.sh patch-bump`, `scripts/check-upstream.sh --record`.
* **Release:** `scripts/version.sh bump minor` → add a `## [X.Y.Z]` section to `custom/CHANGELOG.md` (must contain *Anti-Unsend*, *Edit History*, *Known issues*) → commit → run the workflow with *publish* ticked, or push tag `vX.Y.Z`. A release needs your own signing key, api_id, a matching tag, notes and a verified APK; otherwise nothing is published.

## Decisions that are yours

* **Package name / coexistence.** MyGram uses `org.saimum.messenger`; Java source namespaces remain `org.telegram.messenger`. A matching Firebase config is required via `GOOGLE_SERVICES_JSON_BASE64`. Debug builds add `.beta`, so Firebase needs a second registered Android app (`org.saimum.messenger.beta`) if you build the debug variant with Google Services enabled.
* **Update check.** `BuildVars.CHECK_UPDATES` stays `true` (upstream behaviour). Telegram's update service is not aware of your fork; decide whether to disable it (that would be another source change in the patch).
* **Push notifications** need your own Firebase project (`GOOGLE_SERVICES_JSON_BASE64`); with the upstream file they will most likely not arrive.

## What has and has not been verified

Verified in a sandbox: patch applies to the pristine upstream files, idempotence, conflict report, upstream record/verify/sync (against a local fake upstream), icon generation on the real resource tree (sizes match the originals, repeatable), version tooling, release-notes rules, APK-verification logic (against fake APKs + stub SDK tools), `shellcheck` and `actionlint` clean.

**Not verified (needs one real run):** the Gradle build itself, the exact output path / runtime of `assembleAfatRelease` on GitHub's runners (the workflow searches for the APK instead of assuming a path), real `aapt2`/`apksigner` output formats, and the app on a device. Expect the first CI run to need small adjustments.

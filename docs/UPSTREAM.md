# Upstream maintenance

## Recorded baseline

- Official source: https://github.com/DrKLO/Telegram.git
- Fork origin: https://github.com/ntych/AmeGram-Android.git
- Baseline commit: `f32ffeec763f0bdbf4bec25b52d654f77bff733d`
- Upstream application version: 13.0.2 (7175); this is distinct from AmeGram 0.1.0-alpha.
- Official master resolved to the baseline SHA on 2026-10-15. Do not assume a moving branch will remain there.
- Preserve `.gitmodules`, gitlink revisions, all copyright headers, LICENSE and third-party notices. Upstream README is archived unchanged in TELEGRAM_UPSTREAM_README.md.

## Reproducible checkout

```bash
git clone https://github.com/ntych/AmeGram-Android.git
cd AmeGram-Android
git checkout <reviewed-AmeGram-commit>
git submodule update --init --recursive --depth=1
```

The chosen AmeGram commit pins all submodules. Never use `git submodule update --remote` in a reproducible build. Retain the baseline history; do not squash/import a new source snapshot over the fork.

## Synchronization procedure

1. Start with a clean checkout and create a dedicated `sync/telegram-<version>` branch from the current AmeGram integration branch.
2. Add the official remote if absent: `git remote add upstream https://github.com/DrKLO/Telegram.git`. Fetch it and identify a reviewed full commit SHA, version, Gradle/SDK/NDK changes and license changes.
3. Merge that reviewed upstream SHA on the sync branch. Resolve conflicts without deleting AmeGram behavior or required upstream legal notices. Do not reset the integration branch or force-push.
4. Update submodules exactly to the merged gitlinks. Review new native prebuild provenance and component licenses.
5. Recheck the patch boundaries below, run source/resource/unit checks, build with authorized API/service configuration and perform device login, messaging, calls, notifications and coexistence smoke tests.
6. Update the baseline/version record and changelog. Open a reviewable PR; merge only after owner approval. Do not publish APKs automatically.

## Keep fork patches reviewable

Prefer `org.telegram.messenger.amegram`, `org.telegram.ui.amegram`, `gradle/amegram.gradle`, prefixed strings/resources and documentation. Current upstream touchpoints are the Settings entry, Liquid Glass flag/control, hidden unfinished icon selector, application identity/labels/manifests, per-variant Android account type, BuildVars configuration, debug signing, font-license packaging, focused sensitive-log redaction and truthful SMS app-hash capability selection. Keep networking, MTProto, cryptography, authentication flows and server-side limits upstream.

`org.telegram.*` Java/native class names are implementation namespaces, not AmeGram's install identity. Do not bulk rename them. Preserve Telegram deep-link interoperability and notices. Any change to account type, service registration, package identity or native libraries requires explicit compatibility notes and coexistence testing.

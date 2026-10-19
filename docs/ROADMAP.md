# Roadmap

The feature catalog is a backlog, not a release claim. Each milestone requires tests and a documented verification result.

## 0.1.0-alpha foundation (current implementation)

Repository audit; fork/package boundaries; independent identity preparation; design tokens; native AmeGram Settings entry; one persistent OLED background setting scoped to that screen; existing Telegram settings links; default-off/unavailable experiment infrastructure; first unit and instrumentation tests. See FEATURES.md and VERIFICATION.md for status and limitations. No release has been published.

## Next: first verified debug application

Provide an owner-registered Telegram API ID/hash and service configuration for the independent debug package. Resolve service/manifest integration without fake credentials. Triage the retained lint permission/API/threading errors and move About metadata to a localized format resource; do not blanket-suppress the report. Compile/package the regular debug target, verify native dependencies, install on a disposable emulator/device and test launch, manual login with owner authorization, message send/receive, media, notifications, official-app coexistence, settings persistence after process death, light/dark switching, font scaling and accessibility. No test account credentials belong in logs or source.

## Appearance baseline

Bring the purple design system into selected upstream surfaces incrementally, replace remaining glass/frosted backgrounds with solid surfaces and establish a device-tested reduced-motion policy. Preserve existing themes and navigation until each change has compatibility coverage. Add independent final/adaptive launcher assets. Do not attempt a historical UI restoration in one patch.

## Local productivity and organization

Introduce AmeControl navigation as sections become real. Add local bookmarks/notes/snippets and chat organization tools with explicit account scoping, data cleanup/export documentation and tests. Server folders, sorting and quotas remain subject to Telegram's APIs and restrictions.

## Advanced tools

AmeThemes, AmeFolders, AmePrivacy, AmeTools, AmeBackup, AmeStats, AmeShortcuts and AmeProfiles are planned. Add AmeLab experiments individually with default-off preferences and clearly stated scope. Review local encryption for private notes separately before storing sensitive content.

## Release readiness

Complete license inventory and corresponding-source delivery, independent signing, service registration, privacy/permission documentation, telemetry audit, native reproducibility, security review, device coverage and upstream synchronization drills. Owner approval is required to merge integration branches, publish releases or distribute builds.

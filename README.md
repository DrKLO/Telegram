# AmeGram for Android

**Telegram without the glass.**

AmeGram is an independent, unofficial Telegram client for Android, developed by **Umi**. It is not affiliated with or endorsed by Telegram. The initial development milestone is **0.1.0-alpha**; no release or verified installable APK is available from this milestone yet.

The goal is a maintainable, feature-rich client with modern functionality, classic clarity, purple accents and solid surfaces. Telegram authentication, messaging, MTProto and cryptography remain upstream implementations. There is no mock messaging backend.

## Foundation

- AmeGram Settings entry in the existing Settings screen.
- A persistent, device-local OLED background preference for **AmeGram Settings only**, effective with Telegram's dark theme.
- Links to existing Telegram appearance, folders, notification and privacy settings.
- An informational AmeLab section; no experimental controls are available yet.
- Central light/dark design tokens, English/Russian text, independent temporary launcher monogram and application ID `dev.umi.amegram` (`.beta` for the regular debug build).
- Source-level Liquid Glass feature gate; broader upstream blur/navigation redesign remains future work.

Implementation is not equivalent to device verification. See [verification](docs/VERIFICATION.md) for commands, actual results and blockers.

## Development

Read the current [build audit and CI scope](docs/BUILD_AUDIT.md), [building](docs/BUILDING.md), [architecture](docs/ARCHITECTURE.md), [features](docs/FEATURES.md), [roadmap](docs/ROADMAP.md) and [upstream maintenance](docs/UPSTREAM.md). API credentials are provided through the build environment; never commit or log them. Distribution packaging is intentionally blocked until signing and service integrations are ready. Do not publish the upstream dummy configuration.

The supported first build target is the regular `TMessagesProj_App` debug variant. Other upstream application modules are retained for synchronization, not endorsed as AmeGram distribution targets.

## License and upstream notices

This project derives from [Telegram for Android](https://github.com/DrKLO/Telegram), pinned initially to `f32ffeec763f0bdbf4bec25b52d654f77bff733d` (13.0.2 / 7175). The upstream source headers specify **GNU GPL version 2 or later**; the [LICENSE](LICENSE) contains GPL v2. Existing copyright notices and third-party licenses remain applicable. AmeGram-specific files use `GPL-2.0-or-later`. Publish corresponding source and satisfy component licenses when distributing builds.

The unmodified starting [upstream README](docs/TELEGRAM_UPSTREAM_README.md) is preserved for provenance; its branding and configuration instructions describe Telegram, not AmeGram. Telegram's [API terms](https://core.telegram.org/api/terms), [API registration requirements](https://core.telegram.org/api/obtaining_api_id) and [security guidance](https://core.telegram.org/mtproto/security_guidelines) also apply. Obtain your own API ID, use independent branding, protect user data and publish the source of distributed modifications.

# Changelog

## 0.1.0-alpha — Unreleased

- Audit Telegram Android 13.0.2 (7175), preserve its history and document upstream synchronization.
- Introduce AmeGram package/version preparation, independent temporary launcher branding and per-variant Android account types.
- Read Telegram API configuration from environment variables; block APK packaging without it and block distribution pending integration review.
- Use standard developer debug signing, disable upstream update downloads/official-only passkeys, clear inherited SMS/service identifiers and default logs off.
- Remove identified authentication/push/integrity/CAPTCHA/billing token and push-key/payload log leaks; advertise SMS hash retrieval only when configured.
- Add AmeGram Settings with appearance, organization, notifications, privacy, informational AmeLab and About sections.
- Persist an OLED dark-background preference scoped to AmeGram Settings; introduce purple/light/dark solid-surface tokens and English/Russian strings.
- Disable the gated Liquid Glass effect, omit its control and hide the unfinished alternate-icon selector.
- Preserve upstream legal notices and retain font license assets during packaging.
- Add palette contrast unit tests and local preference instrumentation tests; document actual verification separately.

No APK, release, live login or messaging validation is claimed by this entry.

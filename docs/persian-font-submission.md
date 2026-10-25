# Optional Vazirmatn: upstream submission preparation

Prepared on 2026-10-25. No pull request or remote changes have been made.
The patch is based on `0aae959806c57be8ad457980f2a5c6277ff18635`.
The intended target is `DrKLO/Telegram:master`, whose advertised head was
`3de37c8cfb039452be7398909461da6288babf15` when rechecked on 2026-10-25.
The fork's `origin/master` is not the official upstream head. Compatibility
with the newer official revision still needs validation. The local feature branch is
`feature/optional-vazirmatn-font`. Confirm the target with maintainers if they
request a development branch; do not infer policy from branch names.

## Proposed PR title

Add an optional Vazirmatn font for Arabic-script text

## Proposed PR description

Persian and other Arabic-script text currently use the device's fallback font.
This adds a default-off **Use Vazirmatn font** option in Chat Settings to give
users a consistent bundled font across supported text surfaces. For example,
enabling it changes the Persian part of a mixed Persian/English message and
its contextual punctuation while preserving the English part and inline code.

The implementation applies MetricAffectingSpans before measurement, ellipsizing
and layout. Shared native text views, text/layout helpers and bounded Canvas
label caches provide most integration; specialized renderers also need local
calls where they measure text themselves. Editable text keeps its buffer,
selection and composing spans. The setting persists and invalidates relevant
cached layouts. No network request is needed to load the fonts.

Bundled Regular/Medium/Bold fonts add 368,444 bytes before APK compression and
retain their SIL OFL notice. Existing Telegram Medium maps to Vazirmatn Medium;
native Bold maps to Bold. A central 0.94 size factor applies only to styled
runs in both measuring and drawing. This optical adjustment is open to design
review and is not claimed to match every device's fallback font.

This detects supported Arabic-script characters rather than the Persian
language. Dedicated punctuation is included; shared marks follow nearby
script context. Latin text and ASCII numbers retain their fonts. Code,
monospace, emoji and replacement spans retain their rendering, apart from
invisible counter placeholders whose width must match the visible digits.
WebViews, system UI and decorative story text fonts are outside this scope.

Coverage includes chat messages and date separators, list previews and badges,
archive pull/release hints,
settings and search controls, input fields, AI editing/translation labels,
login/contact labels, two-step verification/passcode introduction labels and
camera mode/attachment labels, QR sharing controls and avatar-picker headers
and emoji-constructor tiles. App-lock titles, explanations and retry messages,
chat greeting cards and empty-chat explanations also use the shared text view.
The emoji/GIF/sticker panel's text tabs use the same integration. Its search
hint stays beside the search icon in both UI directions, with the category
strip reserving the measured width of the styled hint.
Message-sharing sheets also apply the shared text view to their copy-link
actions, including the compact copy button used in the dark presentation.
Letter avatars apply font spans before measuring initials and refresh their
cached layouts when the font setting or text size changes. Latin initials
and emoji keep their existing rendering.
Attachment sizes and download/upload byte counters apply font spans before
measurement and ellipsizing, including document, video and GIF labels. Progress
layouts also refresh at unchanged byte counts when the font setting changes.
Channel comment rows, sponsored-message labels and action buttons, and the
edited-message footer apply spans before measuring. Narrow link-preview
layouts also use styled text in their preliminary line-breaking pass.
Channel/topic pull-up and release prompts and the initial story tutorial now
use styled measurement and rendering. Call-restriction invitation titles and
descriptions, call status/actions, video-preview labels, group-call overlays
and the call-list empty state use the same shared font integration.

Related RTL layout corrections affect the disabled state as well:

- Normalize owner/admin badge glyph origins by subtracting the layout's
  internal line-left offset without moving the badge background.
- Normalize bot-menu label origins and clip text to the visible expansion
  area during animation. These can be separated if preferred.
- Place two-step recovery/skip actions opposite the floating Next button,
  reserving its space in both directions. Keep email resend centered at full
  width when the floating button is hidden.
- Align the emoji-panel search field with its left-side category spacer so
  Persian hints do not overlap the category icons.

Validation so far:

- 33 standalone JDK range regressions passed.
- 206 changed application/instrumentation Java files parsed without syntax
  errors. Parsing does not establish Android compilation or API compatibility.
- Two changed resource XML files, asset presence/header/license and diff
  whitespace checks passed through `Tools/check-persian-font.ps1`.
- Debug and Release APK packaging and signature checks passed before the
  latest story/pull-up/call follow-ups. Debug Java compilation passed after
  those follow-ups; the earlier APKs are not final-state packaging evidence.
- 30 Android correctness tests and a separate comparative text microbenchmark
  executed on the dedicated API 36 emulator. All 30 correctness cases passed
  at font scales 1.0 and 1.5. The synthetic wrapped-layout median increased
  from 0.7134 ms to 5.9846 ms with the font enabled; physical-device profiling
  remains necessary. Details are in
  [persian-font-validation.md](persian-font-validation.md).
- The contributor reports successful manual APK checks on earlier feature
  revisions; the exact revisions, device details and screenshots still need
  to be recorded before treating them as final validation evidence.

This is a draft for feedback on the product choice and implementation scope.
Before requesting final review, attach results for the exact revision using
the evidence form below. The two new localization keys also need to follow
Telegram's translation-platform workflow.

## Review order

The existing commits are preserved. Review the aggregate diff in this order;
the full patch does not need to be read alphabetically.

| Area | Start here | Review concern |
| --- | --- | --- |
| Setting, assets and resources | SharedConfig, ThemeActivity, PersianFontManager, Vazirmatn assets | Default off, persistence, weight mapping, size and license |
| Range detection and spans | PersianTextUtils, PersianFontSpan, PersianFontApplier | Mixed scripts, punctuation heuristic, code/emoji exclusions, formatting |
| Shared rendering/input | PersianTextView, EditTextEffects, StaticLayoutEx, Text, AnimatedTextView | Measurement before drawing, editable identity, setting and typeface refresh |
| Canvas and cached labels | PersianFontCanvasText, MessageObject, FilterTabsView, counters | Bounded caches, invalidation, identical measuring/drawing |
| Specialized integration | ChatMessageCell, DialogCell and native cell/activity replacements | Local pre-measurement integration and coverage |
| Independent RTL behavior | ChatMessageCell adminLayout, BotCommandsMenuView | Bounds with font both enabled and disabled |
| Tests | PersianTextUtilsTest, PersianFontApplierTest, PersianFontPerformanceTest | Distinguish passed API 36 cases and diagnostic timings from outstanding device/visual checks |

If maintainers want smaller review units, separate setting/core integration,
shared rendering, specialized coverage, and independent RTL fixes on a new
submission branch. Do not split by making intermediate commits that reference
missing classes, and do not rewrite the preserved master commits in place.

## Evidence to attach before final review

Record the exact source revision first, then use the same revision for all
results. A change after testing requires assessing which results are affected.

| Evidence | Result to fill in |
| --- | --- |
| Source revision, build variant, build command and result | `afatDebug` test host passed; source hashes and command in validation record |
| Device/model, Android/API version, UI language and font scale | `sdk_gphone64_x86_64`, API 36; correctness at 1.0 and 1.5; Persian/English visual UI comparison pending |
| All instrumentation test count, failures and runner output | 30 correctness cases passed twice, zero failures; 1 diagnostic benchmark passed; logs recorded locally |
| Older supported API and a current supported API | API 36 passed; older API pending |
| Off/on screenshots of the same Persian/English mixed chat and settings page | Pending |
| Same-size off/on screenshots of badges, dates, proxy subtitle and bot menu | Pending |
| Contacts, AI editor/tooltip, story camera and chat attachment camera | Pending |
| Passcode introduction, two-step verification footers in RTL/LTR, narrow/landscape widths, large font scale and centered email resend | Pending |
| Draft editing, bold/italic, code, emoji, composing input and selection | Pending |
| Restart persistence, returning to cached screens and additional panes | Pending |
| Accessibility large font scale, TalkBack and selection handles | Pending |
| Same-device off/on frame times, allocations and scroll/typing workload | Pending |
| APK size difference for the same build variant | Pending |

Keep the test workload identical between settings. Useful workloads include a
long mixed-script message with formatting, rapid typing with composition,
scrolling a populated chat list and switching folder tabs. Publish measured
numbers and the capture method; visual smoothness is not performance evidence.

The scope and the proposed font/scale still need maintainer agreement. Source
checks and a clear PR cannot guarantee review, merge, or a release date.

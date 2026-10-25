# Optional Vazirmatn font

Enable **Use Vazirmatn font** in Settings > Chat Settings. The setting is off by
default and persists in the global main preferences. Changing it rebuilds the
fragment views; cached message layouts check the setting before reuse.

Arabic-script letters, Persian/Arabic digits, dedicated Arabic punctuation
(including question mark, comma, semicolon, percent and numeric separators),
and their combining marks and joiners receive font spans. Shared punctuation
such as exclamation marks, periods, quotes and parentheses follows the nearby
script within the same line. Opening quotes/brackets prefer the following
script; other punctuation prefers the preceding script. Whitespace itself is
not styled. This is script detection with a punctuation heuristic, not language
detection. Latin text, ASCII digits, punctuation in Latin text, isolated shared
punctuation with no script context, and emoji keep their existing fonts.
AnimatedTextView resolves spans before splitting text into animated parts so
punctuation retains its full-text context during animations and size changes.
Regular, Medium, and Bold assets are distributed with their SIL OFL license.
Telegram's Medium assets, including the typeface returned by AndroidUtilities.bold()
and rich-message bold formatting, map consistently to Vazirmatn Medium (500).
Native Bold/StyleSpan.BOLD and bold headers map to Bold (700); regular maps to
Regular (400). PersianFontSpan applies an optical text-size factor of 0.94 to
the styled script run in both measurement and drawing. This keeps Vazirmatn
slightly smaller and visually lighter while retaining the weight hierarchy.
The base TextPaint, Latin runs, ASCII numbers, emoji, and code keep their size.
Do not apply another size factor at individual call sites. This is an initial
visual adjustment, not a calibrated match to every device's fallback font.

The shared integration points are PersianTextView, SimpleTextView,
AnimatedTextView, Text, StaticLayoutEx, and PersianFontCanvasText.
LinksTextView, SpoilersTextView, TextViewEmojis, and TextViewWithLoading inherit
the native text-view integration, including typeface and setting refresh.
Settings cells, dialog previews, message bodies, sender
names, forwarded headers, replies/quotes, and the chat/story caption inputs have
additional integration where they measure or build text themselves.

Code and monospace spans and replacement spans keep their original rendering.
EmptyStubSpan is an exception: invisible animation placeholders receive font
metrics so hidden digits reserve the same width as the visible digits.
Font application preserves the original characters and other spans. Editable
text is styled in place without replacing its text, selection, or composing
spans. Unchanged font spans are reused when typing. EditTextEffects handles
text edits; EditTextCaption refreshes font spans before notifying its page
delegate about rich-formatting changes, even when no delegate is installed.
Do not duplicate those font passes in individual input owners.

Direct Canvas.drawText calls, independent TextViews, WebViews, system surfaces,
and decorative story text renderers are not globally intercepted. Chat-list
previews, the NumberPicker wheel, theme names, wallpaper controls, outline input
labels, and SlideChooseView use the Canvas helper. Its per-view cache holds at
most 32 labels and invalidates when the font setting or measured paint
properties change. Unstyled labels retain native Canvas.drawText rendering.
Chat-list and folder-tab badges (both tab implementations), shared counters,
search counters, boost badges, unread-panel labels, and localized poll
percentages use styled measurement and drawing. Bot-ad titles,
explanation links, and sponsor-information panels use the native text-view
integration; sponsored-message title labels also measure their styled text.
The animated proxy subtitle, account-info actions, and native login text views
(including country selection and phone-number confirmation) use PersianTextView.
Owner/admin labels receive spans before measuring. Their text origin and the
bot-menu text origin subtract StaticLayout.getLineLeft(0), preserving the
existing button/badge geometry for RTL and LTR text even with Vazirmatn off.
The bot-menu label is clipped to the visible expansion area during animation.
Folder titles refresh their font spans when their title, base weight, italic
state, or font setting changes rather than rebuilding font spans every frame.
Integrate new
native text surfaces with PersianFontApplier.applyForPaint before measuring or
ellipsizing text. Prefer PersianTextView for native TextViews and
PersianFontCanvasText for short, single-line Canvas labels, using the same
helper for measurement and drawing. Use applyToEditable for editable text.
Do not change a shared
TextPaint's typeface globally, as that would affect Latin text too.

## Validation

From the repository root, run the repeatable source checks with a JDK 17 or
newer and PowerShell:

```powershell
powershell -NoProfile -File Tools/check-persian-font.ps1
```

The script parses changed application/instrumentation Java files, compiles and
runs the standalone range suite, checks changed resource XML, reports asset
hashes/size and checks diff whitespace against the pinned upstream base. It
includes untracked source files. It does not invoke Gradle, adb or the app.
Pass `-BaseRef <revision>` when deliberately reviewing against another base.
Generated check files stay under `build/font-check/review`.
The script also writes `result.json` with the base/HEAD revisions, per-input
SHA-256 hashes and source-check results. Keep that snapshot with device logs
when testing uncommitted changes; a HEAD revision alone does not identify a
dirty working tree. Git line-ending warnings are suppressed per invocation,
without changing repository or global Git configuration.

The instrumentation suite is
`org.telegram.ui.Components.PersianFontApplierTest` in TMessagesProj_AppTests.
It contains 30 tests covering bundled fonts, script boundaries, formatting,
links, code, replacement spans, repeat application, disabling, selection, IME
composition, native text-view variants, rich input without a page delegate,
folder-title refresh, Canvas cache/rendering behavior, weight consistency,
Persian-digit counter refresh and placeholders, bot-ad text surfaces, optical
sizing, animated proxy subtitles, and bot-menu rendering with both scripts and
both font settings, dedicated/contextual punctuation, formatting exclusions,
animated punctuation fragments, and archive pull/release labels refreshed at
the same width after font toggles, and mixed-script avatar initials refreshed
after font toggles and text-size changes without styling Latin initials.
Attachment-progress coverage checks localized and Latin digits across document,
voice, music, video and GIF types, cached reuse, measured widths and font toggles
at unchanged byte counts.

The Android rendering changes have passed Java syntax, diff whitespace and
Debug Java compilation checks; the standalone detector tests also passed.
Earlier Debug/Release APKs predate the story/pull-up/call follow-ups. See
[persian-font-review.md](persian-font-review.md) for outstanding release evidence.


The standalone range detector suite needs only a JDK and does not build or run
the Android app. Its 33 regressions cover dedicated and contextual punctuation,
mixed scripts, quotes/brackets, numbers, newline boundaries, emoji/keycaps,
joiners, malformed surrogates and linear access on a long punctuation run:

```powershell
javac -encoding UTF-8 -d build/font-check/punctuation-tests TMessagesProj/src/main/java/org/telegram/messenger/PersianTextUtils.java Tools/tests/PersianTextUtilsTest.java
java -cp build/font-check/punctuation-tests PersianTextUtilsTest
```

These standalone checks passed. All 30 Android instrumentation tests also
passed on the dedicated API 36 emulator at font scales 1.0 and 1.5. See
[persian-font-validation.md](persian-font-validation.md) for build identity,
fixture corrections, performance measurements and remaining device checks.

On Windows PowerShell, quote the entire Gradle project-property argument:

```powershell
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_App:assembleAfatDebug
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_AppTests:connectedAfatDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=org.telegram.ui.Components.PersianFontApplierTest'
```

To run only the Java font suite without generating or compiling the unrelated
protocol fixtures, use the optional init script:

```powershell
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_AppTests:connectedAfatDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=org.telegram.ui.Components.PersianFontApplierTest' -I Tools/persian-font-tests.init.gradle -x :TMessagesProj_AppTests:generateScheme --max-workers=4
```

Before release, check Persian and English UI languages, restart persistence,
toggle off/on while returning to existing chats, mixed-script messages, bold and
italic links, spoilers, inline/block code, emoji, RTL replies/quotes, forwarded
messages, editing/drafts, story captions, accessibility text, and large text
sizes. Check that ellipsizing, wrapping, and touch targets follow the styled
layout. Device testing is needed in addition to compilation and span tests.

If Gradle's connected-test runner cannot resolve its UTP dependencies, build
`:TMessagesProj_AppTests:assembleAfatDebug` and
`:TMessagesProj_AppTests:assembleAfatDebugAndroidTest` with the same init script,
install the two APKs from that module with `adb install -r`, and run:

```powershell
adb shell am instrument -w -r -e class org.telegram.ui.Components.PersianFontApplierTest org.telegram.messenger.web.test/androidx.test.runner.AndroidJUnitRunner
```

This uses the separate `.web` test host. Check the instrumentation output for
the actual test count and failures; the command's shell exit code alone does
not establish success.

`PersianFontPerformanceTest` is a separate comparative microbenchmark for
applying fonts, wrapped layouts, repeated styling and warm Canvas measurements.
It alternates the off/on order and reports median elapsed time over nine
rounds. The optional ART allocation counter is process-wide and includes
background activity. These results are not scroll/typing frame timings or
performance acceptance thresholds. Run it in the dedicated test host after
the correctness suite, never on a device being used for manual testing.
The test host requires API 26; application behavior on API 23-25 therefore
still needs separate device validation.

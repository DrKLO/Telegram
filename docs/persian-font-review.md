# Vazirmatn implementation review

Updated on 2026-10-25 for the feature branch based on upstream
`0aae959806c57be8ad457980f2a5c6277ff18635` (13.0.3). Source checks and Java
compilation have passed; build and device evidence are distinguished below.
The earlier changes are recorded
in `0e2d89dbb` and `50351d48d`; follow-ups are recorded in `2397c3457`
(coverage) and `86aad3f9d` (independent RTL fixes). Use `git rev-parse HEAD` to identify the exact
revision for any subsequent device evidence.

## Assessment

The implementation is suitable for further validation as an optional fork
feature. It is not yet supported by enough evidence for an upstream-ready
submission. The patch reaches many UI files because Telegram has several
independent text-rendering paths; coverage alone does not establish correctness.

## Findings addressed

| Finding | Change | Regression coverage added |
| --- | --- | --- |
| Formatting refresh depended on chat/caption page delegates. Other rich inputs could retain the previous font weight or font spans over newly formatted code. | All EditTextCaption formatting notifications now refresh font spans centrally before the existing delegate callback, including when no delegate is installed. | Bold, regular, monospace and return to regular without a delegate; buffer and selection preservation. |
| Several cells applied font spans before passing text to a font-aware TextView, repeating work. | Removed redundant passes in settings/detail/check/header/privacy cells and menu/dialog text. Text edits use the shared EditTextEffects watcher rather than extra page watchers. | Existing span idempotence tests and the native-view integration test. |
| Native text variants only applied fonts on setText. Setting a typeface afterward could leave the wrong Persian weight. | Emoji, link, spoiler and loading text views now inherit PersianTextView and retain their own emoji/spoiler lifecycle handling. | Set text first, then bold and regular; disable and measure the five view variants. |
| Refreshing an editable PersianTextView replaced its buffer. Direct paint bold/italic changes also escaped the previous setting-only measure check. | Update Editable spans in place; refresh when typeface, fake-bold or skew changes. | Editable buffer identity and selected range across bold and disabling. |
| Folder widths reapplied the full font transformation on repeated calls during drawing. | Cache each owned title's applied font state; reapply when title or font state changes. | Repeated calls preserve the title; changing the setting and weight refreshes it. |
| SlideChooseView maintained a separate Canvas font layout implementation. | Reuse PersianFontCanvasText for both width and baseline drawing. | Shared Canvas cache setting/paint changes, caller-paint preservation and unchanged Latin rendering. |

## Architecture retained

- SharedConfig persists one optional, default-off setting.
- PersianTextUtils detects supported Arabic-script characters, including Persian
  and Arabic digits and dedicated punctuation. Shared punctuation follows
  nearby script context within a line. It does not identify the language.
- PersianFontApplier owns span generation, formatting exclusions and editable
  reconciliation. Renderers must apply it before measuring or ellipsizing text.
- PersianTextView owns native text-view integration. Existing public TextView
  field and return types remain compatible.
- PersianFontCanvasText owns bounded per-view caches for short Canvas labels;
  it leaves the caller's Paint unchanged and keeps native rendering for labels
  without font spans.
- MessageObject checks its font setting before reusing a message layout.
- Formatting, code/replacement exclusions and other spans stay in their
  original owners; no global shared paint typeface is changed.

The patch intentionally retains specialized integration where a component
measures text before creating a shared layout. Removing those calls merely to
reduce the number of changed files would risk incorrect widths and ellipsizing.

## Remaining evidence before submission

| Required evidence | Current state |
| --- | --- |
| Java syntax parse across the font-related working files | Passed for 206 application/instrumentation Java files, including the comparative performance test. |
| Standalone range detector regressions | All 33 passed on the JDK; includes mixed-script punctuation, malformed surrogates and linear character access on a long punctuation run. |
| Whitespace, resource XML and font asset checks | Passed through `Tools/check-persian-font.ps1`: two XML files, three TrueType headers and the SIL OFL notice. Separate from Android compilation and font rendering. |
| Application compilation and packaging | Debug and Release APKs were built and signature-verified before the story/pull-up/call follow-ups. Debug Java compilation passed after those follow-ups. Those earlier APKs do not validate the later changes. |
| All 30 instrumentation tests on the final source state | Passed on dedicated API 36 emulator at font scales 1.0 and 1.5; two initial fixture failures were corrected. See the validation record. |
| Older and current supported Android versions | API 36 passed; older versions remain untested. Test host minimum is API 26; application minimum is API 23. |
| Persian and English UI, mixed messages, links, code, emoji and rich editing | Needs final UI regression testing. |
| Toggle while returning to existing chats, secondary navigation panes and cached UI | Needs device verification; rebuilding one navigation stack is not proof that every independently hosted surface refreshes. |
| Large accessibility font sizes, selection handles, composing input and TalkBack | Needs device verification. |
| Frame time and allocation comparison with the feature off and on | Text microbenchmark measured: synthetic wrapped layout median 0.7134 ms off / 5.9846 ms on. Physical-device frame timing remains unmeasured; this cost needs follow-up. |
| Screenshots and exact tested revision/build variant | Not collected for the final state. |

Use identical chat contents and devices for performance comparisons. Include a
long Persian message, mixed formatting, typing with composition, a busy chat
list and folder-tab scrolling. Record frame timings and allocations; do not
infer performance from visual smoothness alone.

## Submission preparation

Keep the change limited to font integration, assets/license, relevant strings,
tests and documentation. The two feature commits are local and their upstream
base matched the official master head when checked on 2026-10-24. Development
continues on `feature/optional-vazirmatn-font`; no published history has been
rewritten. Upstream also has dev and dev2 branches. Their existence does not
establish a contribution policy or make either a better PR target than master.
The official upstream was checked again on 2026-10-25 using `git ls-remote`:
`master` is now `3de37c8cfb039452be7398909461da6288babf15`, while the fork's
`origin/master` still points at the older base above. Compatibility with the
new upstream head has two text conflicts and a replaced QR flow requiring a
port; see [persian-font-validation.md](persian-font-validation.md). No rebase, checkout or history rewrite
was performed during the contributor's build/install session.

Three bundled font files total about 360 KiB before APK compression. Keep their
SIL OFL notice with the assets. The repository README routes localization to
Telegram's translation platform; the two new keys still need to follow the
maintainers' localization workflow for an upstream submission.

The English PR draft, review order, known tradeoffs and device evidence form
are in [persian-font-submission.md](persian-font-submission.md). The preparation
files are local; no push or pull request has been created.

The latest coverage and independent emoji-search/two-step RTL changes are
separate commits. Earlier owner/admin and bot-menu changes remain in the
preserved history and are explicitly called out below.

Two RTL corrections also change behavior when the option is off: owner/admin
badge text removes its internal StaticLayout line-left offset, and bot-menu
text removes the same offset and clips to its animated visible area. These
are source-level fixes to the baseline, not proof of a stock-APK defect. They
must be called out explicitly in the PR; they can be submitted separately if
maintainers prefer. Existing instrumentation covers bot-menu bounds with both
font settings; all 30 correctness cases passed on the dedicated API 36 emulator.

The centralized 0.94 optical scale is a visual choice, not a measured universal
match to Android fallback fonts. Arabic-script selection also affects Arabic,
Urdu and other text using these blocks. Review those product choices rather
than describing this feature as Persian language detection. The current code
still constructs candidate spans before discovering an already styled result
is equivalent; idempotence is not a zero-allocation performance guarantee.

The security-screen follow-up also replaces native labels in ActionIntroActivity
(including the initial passcode screen) and both two-step verification screens.
Recovery/skip actions align opposite the floating Next button and reserve its
side of the footer in RTL and LTR, including their touch areas. The full-width
centered email resend state clears that reservation when Next is hidden.
This layout correction applies with the font disabled too. Verify narrow and
landscape layouts, large fonts, both directions and the email confirmation
state on a device; these cases have not been run for this follow-up.

Upstream acceptance also depends on the maintainers wanting this product
feature and approving its scope. This review does not establish that policy.

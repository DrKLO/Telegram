# Vazirmatn implementation review

Reviewed on 2026-10-24. This is a source review of the current working tree,
including earlier font changes. No application build, emulator, installation,
instrumentation run, or device profiling was performed for this review.

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
  and Arabic digits. It does not distinguish Persian from Arabic language.
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
| Java syntax parse across the font-related working files | Passed for 144 Java files; this does not resolve symbols, Android API compatibility or resources. |
| Whitespace and local resource checks | Checked separately from compilation. |
| Application build for the final source state | Not run in this review, as requested. |
| All 17 instrumentation tests on the final source state | Not run. Six regression tests were added to the existing eleven. |
| Older and current supported Android versions | Needs device/emulator execution. |
| Persian and English UI, mixed messages, links, code, emoji and rich editing | Needs final UI regression testing. |
| Toggle while returning to existing chats, secondary navigation panes and cached UI | Needs device verification; rebuilding one navigation stack is not proof that every independently hosted surface refreshes. |
| Large accessibility font sizes, selection handles, composing input and TalkBack | Needs device verification. |
| Frame time and allocation comparison with the feature off and on | Not measured. Reduced duplicate calls are not a performance benchmark. |
| Screenshots and exact tested revision/build variant | Not collected for the final state. |

Use identical chat contents and devices for performance comparisons. Include a
long Persian message, mixed formatting, typing with composition, a busy chat
list and folder-tab scrolling. Record frame timings and allocations; do not
infer performance from visual smoothness alone.

## Submission preparation

Keep the final change limited to font integration, assets/license, relevant
strings, tests and documentation. Review the full diff against the intended
upstream base before preparing commits; the working tree currently includes
both tracked and untracked files. No commits or remote changes were made by
this review.

Three bundled font files total about 360 KiB before APK compression. Keep their
SIL OFL notice with the assets. The repository README routes localization to
Telegram's translation platform; the two new keys still need to follow the
maintainers' localization workflow for an upstream submission.

Suggested title: **Add an optional Vazirmatn font for Arabic-script text**

Suggested description, to update with real validation results before sending:

> Add a default-off Chat Settings option that uses bundled Vazirmatn weights for
> supported Arabic-script text while preserving Latin text, code, emoji and
> existing formatting. Native views, shared layouts and selected Canvas controls
> apply font spans before text measurement. Include the SIL OFL notice and
> regression tests for formatting, editing, font toggling and cached labels.
>
> Validation: replace this line with the final build variant, tested Android
> versions, full instrumentation results, UI examples and performance evidence.

Upstream acceptance also depends on the maintainers wanting this product
feature and approving its scope. This review does not establish that policy.

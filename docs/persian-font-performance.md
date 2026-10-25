# Font processing optimization

Measured on 2026-10-25 against application commit `637d6c419`, using the
dedicated `emulator-5554` (`sdk_gphone64_x86_64`, Android 16 / API 36).
These results describe the optimization committed after that baseline. Input
hashes were captured locally with the raw benchmark and correctness output;
generated evidence is excluded from the repository.

## Implementation

Previously, repeated styling copied the complete text, removed and rebuilt
font spans, then compared that temporary result with the original. Editable
reconciliation also used that temporary copy.

The applier now computes desired ranges before allocating replacement font
spans or copying a styled input. Matching text is returned unchanged. Editable
inputs reconcile those ranges directly, preserving unchanged spans, selection
and composing state. Disabling or finding no supported script removes only
font spans directly. Plain unformatted text uses a direct construction path.

The per-call plan stores start/end/style triples in an integer array and
resolves each required font weight once. Indexed loops avoid per-word iterator
allocations. Formatting and mutable link style flags are rechecked on every
application; results are not cached by mutable text identity. Script ranges,
formatting exclusions, optical size and font weights retain their previous rules.

## Method

The expanded `PersianFontPerformanceTest` was run three times against the
previous installed host, then three times against the final optimized host.
Both phases used the same benchmark implementation. A later test-only change
adds an ASCII-editable regression without changing that benchmark.
Each invocation uses nine alternating off/on rounds with warmups. The table
reports the median of the three per-invocation medians, with the font enabled.
Mixed text repeats the same Persian/English/digit/emoji phrase 40 times.
Formatted input adds a bold span to each phrase. Measurements use font scale 1.0.

| Operation | Before, ms/op | After, ms/op |
| --- | ---: | ---: |
| Apply to unformatted mixed text | 0.6369 | 0.3866 |
| Apply to short Latin text | 0.0041 | 0.0042 |
| Construct wrapped mixed-text layout, 720 px | 4.2988 | 4.1855 |
| Measure short Canvas label with warm cache | 0.0044 | 0.0059 |
| Reapply to already-styled mixed text | 1.2831 | 0.4579 |
| Refresh unchanged editable mixed text | 1.2036 | 0.2170 |
| First application to formatted mixed text | 1.3532 | 1.4393 |
| Reapply to formatted mixed text | 2.8189 | 0.6222 |

Repeated mixed-text application used approximately 8.7 KB process-allocated
bytes per operation versus 28.0 KB before. Formatted reapplication used about
14.2 KB versus 97.5 KB. These ART counters include other app threads and are
diagnostic allocation measurements, not isolated per-thread counts.

The principal improvement is repeated processing and editable reconciliation.
Full wrapped-layout construction remains costly. First application to formatted
input was about 6% slower in this sample and used approximately 56.1 KB versus
49.7 KB, reflecting the added plan metadata. Canvas-cache and Latin controls
also varied; small timing differences are not treated as reliable improvements.
This is not evidence of a comparable improvement in real-device scrolling,
typing latency, release performance or startup reliability.

## Validation and evidence

- Final `afatDebug` host and instrumentation APK builds passed.
- All 33 correctness instrumentation tests passed at font scales 1.0 and 1.5;
  the original emulator scale 1.0 was restored.
- New regressions check unchanged editable span notifications, mutable link
  formatting, moved formatting, script replacement and stale font cleanup on
  Latin input while retaining selection and unrelated formatting.
- All 33 standalone range regressions, syntax checks for 206 Java files,
  two resource XML checks, font/license checks and diff checks passed.
- One intermediate post-install instrumentation launch failed before any tests
  with a process-start ANR. Logs identify the startup timeout; the unchanged
  package passed on retry. The final installed package passed both complete
  correctness runs without that failure. No timeout setting was relaxed.

Raw evidence is under the ignored `build/font-check/performance-optimization`:
`before-expanded-{1,2,3}.txt`, `after-{1,2,3}.txt`, `comparison.json`,
`correctness.txt`, `correctness-font-scale-1.5.txt`, `source-check.txt`, build
logs and `evidence.json`. Intermediate attempts are retained separately and
are excluded from the final comparison. APKs, source/log hashes and generated
files are local artifacts, not tracked binaries. No user build was stopped.

The contributor subsequently reported satisfactory Debug scrolling on a
physical device. This does not replace frame-time measurements. Older Android
versions and visual accessibility checks remain unmeasured. See
[persian-font-validation.md](persian-font-validation.md) for the current build
record and upstream compatibility limits.

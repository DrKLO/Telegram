# Optional Vazirmatn validation

Recorded on 2026-10-25. This summarizes completed checks; it does not claim
that every Android version, UI state or accessibility configuration was tested.
See [persian-font.md](persian-font.md) for implementation and reproduction
commands and [persian-font-performance.md](persian-font-performance.md) for
the comparative measurements.

## Completed checks

| Check | Recorded result |
| --- | --- |
| Standalone script-range regressions | All 33 passed on the JDK. |
| Java syntax | 206 application/instrumentation files parsed. |
| Resource and asset checks | Two resource XML files, three TrueType headers and the SIL OFL notice passed. Bundled fonts total 368,444 bytes before compression. |
| Android correctness | All 33 instrumentation cases passed on an API 36 emulator at font scales 1.0 and 1.5. |
| Processing comparison | Three before/after benchmark runs per phase; results and tradeoffs are documented separately. |
| Latest Debug package | :TMessagesProj_App:assembleAfatDebug completed successfully; APK signature v1/v2 and all three font assets verified. Package: org.telegram.messenger.beta, version 13.0.3 (71839). |
| Isolated Release package | afatRelease built with minification and resource shrinking; signature v1/v2 verified. Its temporary package/manifest configuration stayed outside tracked sources. |
| Contributor device feedback | Contributor reports broad UI coverage and satisfactory Debug scrolling on a physical device. This is manual feedback, not a frame-time benchmark. |

The Android tests cover script boundaries, contextual punctuation, weight and
italic handling, links, code/replacement exclusions, editable identity,
selection/composition, repeated application, mutable formatting, stale-span
removal, native text views, Canvas caches and specialized label integration.
They do not exhaustively exercise all affected screens.

Two early fixture failures were corrected before the successful correctness
runs. An intermediate post-install instrumentation launch timed out before
running tests; retrying the unchanged package succeeded. The final host passed
both complete correctness runs. These events are not counted as successful
test executions.

Generated APKs, raw logs, input hashes and local packaging configuration remain
under ignored build directories. They are not repository deliverables. Local
evidence was captured before these results were committed; input snapshots
identify the tested application and instrumentation sources.

## Limits

- Android versions below API 36 have not been validated. The application
  minimum is API 23; the existing instrumentation host minimum is API 26.
- No controlled physical-device frame-time, typing-latency, TalkBack or complete
  accessibility/landscape matrix was collected.
- No matched before/after APK-size comparison or complete screenshot set was
  collected. Asset bytes are not an APK-size delta.
- The isolated Release package reached startup, but account login testing was
  blocked. Phone diagnostics returned API_ID_PUBLISHED_FLOOD; the temporary
  package also had incompatible Firebase configuration. These configuration
  issues were not changed in application sources and do not validate Release
  chat performance. Fork builds need their own API and service configuration,
  as described by the repository's compilation guide.

## Upstream compatibility

The feature remains based on 0aae959806c57be8ad457980f2a5c6277ff18635.
Official DrKLO/Telegram:master, fetched on 2026-10-25, was
3de37c8cfb039452be7398909461da6288babf15.
A read-only three-way merge check reports conflicts in ShareAlert.java and
values/strings.xml, plus deletion of the modified QrActivity.java.
The replacement QR flow needs its font integration ported when updating the
base. No upstream merge or history rewrite was performed during packaging.

The contributor approved publishing the current feature snapshot without
additional tests. The newer upstream port remains a separate step before the
branch can be merged into current official master. Maintainers also need to
review the default-off setting, Arabic-script scope, 0.94 optical size factor,
independent RTL corrections and localization through Telegram's translation
platform.

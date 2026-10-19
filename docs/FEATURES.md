# Feature status

## Current implementation

| Feature | Status and scope |
| --- | --- |
| AmeGram Settings entry | Implemented inside upstream Settings; active account is propagated |
| Appearance | Existing Telegram themes link; independent AmeGram screen palette |
| OLED background | Implemented persistent local preference; only AmeGram Settings in dark mode |
| Chat organization | Link to real upstream folder settings; no additional server quota promised |
| Notifications | Link to real upstream notification settings |
| Privacy | Link to real upstream privacy/security settings |
| AmeLab | Informational, noninteractive placeholder; no experiments available |
| About | Version, developer, slogan and unofficial-client notice |
| Identity | Independent package, alpha version, localized app labels and temporary A monogram; APK/service verification remains blocked |
| Liquid Glass | Runtime/settings gate disabled and control omitted; wider upstream blur/navigation cleanup is pending |
| Local storage | `amegram_settings` SharedPreferences; one boolean, no account or message data |
| Tests | Palette unit tests and preference instrumentation tests added; actual execution recorded separately |

“Implemented” describes source changes, not a verified installable application. See VERIFICATION.md.

## Long-term product backlog

All items below are **planned**, except the narrow pieces explicitly described above. Numbers match the initial product catalog.

| Range | Area | Planned capabilities |
| --- | --- | --- |
| 1–15 | Advanced appearance | Live theme editor; custom accents; per-chat appearance; bubble shapes; message spacing; chat-list density; navigation layout; classic navigation; background dimming; app icons; font size/weight; compact mode; reduced animations; theme import/export; scheduled themes |
| 16–27 | Chat organization | Local presets within practical limits; advanced folders; local labels; folder colors/icons; prioritization; filters; unread views; archive behavior; saved filters; workspaces; chat notes; supported sorting |
| 28–40 | Messaging productivity | Local drafts; templates; snippets; Markdown help; send preview; send-button preferences; local reminders; saved-message collections; bookmarks; tags/annotations; copy/export formatting; improved selection; quick actions |
| 41–50 | Media and files | Download manager; type filters; local browser; storage visualization; cache policies; duplicate detection; sorting; queue UI; per-chat storage; local file organization |
| 51–60 | Privacy and security | Privacy dashboard; supported Android app lock; notification privacy; cleanup; supported session overview; privacy defaults; storage documentation; permission explanations; secure authentication handling; optional encrypted local notes |
| 61–70 | Notifications | Per-chat profiles; categories; quiet hours; grouping; priority; sounds; vibrations; previews; folder preferences; diagnostics |
| 71–80 | Accessibility and usability | One-handed mode; larger targets; screen-reader labels; high contrast; gestures; keyboard navigation; landscape; tablets; simplified interface; animation intensity |
| 81–90 | AmeGram tools | AmeControl; AmeLab; AmeThemes; AmeFolders; AmePrivacy; AmeTools; AmeBackup; AmeStats; AmeShortcuts; AmeProfiles |

No unsupported server behavior, privacy bypass, covert monitoring or hidden access is in scope. Telegram server limits remain authoritative. New experimental capabilities must be individually opt-in. Unfinished interactive controls must be omitted or presented as clearly noninteractive information.

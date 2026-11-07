# defaults — widgets drawn by the renderer's own styles

Six widgets written with little or no CSS. Since v0.1.1 the renderer's
user-agent layer (`docs/sdk-v1.md` §2.4, `uaStylesheet` in
`packages/core/profile.json`) gives the root the bubble's colours and the
client's text tokens, and gives headings, paragraphs, lists, tables, quotes,
code, links and `<button>` explicit defaults, so these look like the client's
own messages on every client: in its text size, on its bubble's colour, with
its button. They are the examples to start from when a widget should look
native; any CSS a widget adds wins over the layer.

| File | CSS | What it shows |
|---|---|---|
| `static/01-text-card.html` | none | a title, paragraphs, `strong`, `em`, `small` |
| `static/02-list.html` | none | a numbered list with a bulleted list inside it |
| `static/03-table.html` | one rule (numbers to the right) | caption, header row, separator lines, outer cells flush with the text |
| `static/04-link-card.html` | none | a link (the profile turns it into `openLink` on a tap) and a `<button>` calling `TelegramWidget.openLink` |
| `interactive/05-faq.html` | one rule (full-width buttons) | questions as `<button>`s that show and hide their answers (`hidden`) |
| `interactive/06-survey.html` | two rules (the result bars, in `--tg-theme-button-color`) | an answer per `<button>`, then the results; the vote kept in `localStorage` |

Rules: no form controls (outside profile 1), no named fonts, no colours of
their own where a theme variable fits, under 3800 bytes compressed and 560 px of
height at a 320 px wide bubble (all of them: 0.3–1.0 KB, 120–250 px).
Every file carries an `idea:` line, which the test group's Defaults topic posts
as its description (`scripts/conformance/publish-channel.ts`).

`corepack pnpm test:defaults` runs them in the sandbox and in reference mode
(the same UA layer with the reference tokens) → `docs/compat-report-defaults.md`.

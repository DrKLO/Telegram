# Web Animations fixtures

Reference output of the Telegram macOS app's Web Animations path, used by
`tests/unit/web-animations.test.ts`.

- `waapi-*.html`: the TwidgetLab Web Animations widgets (`tools/perflab/TwidgetLab/widgets` in the app).
- `app-anims.json`: for every test widget, the raw `anim` batches and console logs the app's
  `sandbox-bundle.js` produced in the Node harness (fixed clock and random bytes). The new shim must match them.
- `app-validator.json`: `__twValidator.validateAnim` output and diagnostics of the app's `validator-bundle.js`
  for the ops and anim stream of every widget plus the generated `synthetic` and `array-values` streams
  (those streams are rebuilt by the test, not stored). Outputs over 4 KB are stored as SHA-256.
  The one accepted difference: keyframe `values` given as an array are rejected here, the app accepted them.

Captured from `sam/new-features` at `d8ea9aa82`. To regenerate:

```sh
APP=/path/to/telegrammacos
git -C $APP show <rev>:packages/WidgetSandbox/Sources/WidgetSandbox/WidgetJS/sandbox-bundle.js > /tmp/app-sandbox-bundle.js
git -C $APP show <rev>:packages/WidgetSandbox/Sources/WidgetSandbox/WidgetJS/validator-bundle.js > /tmp/app-validator-bundle.js
cd packages/core
TW_APP_SANDBOX_BUNDLE=/tmp/app-sandbox-bundle.js TW_APP_VALIDATOR_BUNDLE=/tmp/app-validator-bundle.js \
  npx tsx --test tests/unit/web-animations.test.ts
```

Without the variables the test compares against the stored files.

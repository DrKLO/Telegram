# Widget assets: images and icons

A twidget widget is **one self-contained HTML document**. There is no bundle format, no asset folder and no named image a client has to ship. Everything a widget shows comes from the document itself, or from one icon set that every client bundles identically.

| Source | Use it for | Fetches anything? |
|---|---|---|
| `data:` URL | Pictures, logos, charts, sparklines, any bitmap or SVG | Never |
| Inline `<svg>` | Vector drawings that change with the widget's state | Never |
| `icon:` URL | UI icons from the built-in set | Never |

Every client must render these three the same way. Nothing else is an image source: `http:`, `https:`, `file:`, `blob:` from elsewhere, relative URLs and host-specific schemes are dropped by the validator.

## `data:` URLs

Allowed MIME types (the validator's `imageMIMETypes`): `image/png`, `image/jpeg` (`image/jpg`), `image/gif`, `image/webp`, `image/avif`, `image/bmp`, `image/x-icon` (`image/vnd.microsoft.icon`) and `image/svg+xml`. For PNG, JPEG, GIF and WebP every client can decode; AVIF, BMP and ICO are best effort.

`data:image/svg+xml` is an **SVG image**, not a document:

- It is drawn by the client's sanitized SVG renderer, the same one that draws inline `<svg>`, with the same tag and attribute allowlist. Scripts, `on*` handlers, `<foreignObject>` content, `<style>` imports and every external reference (`href`, `xlink:href`, `url()` that isn't a `#fragment`) are dropped.
- It loads no images of its own, except nested `data:` bitmaps. Nested SVG images and `icon:` URLs inside it render nothing, so an SVG image can't recurse.
- Its size is its `width` and `height` attributes in CSS px. Without them, a `viewBox` gives the aspect ratio and the width is 300 px; without both, it is 300 × 150.
- `currentColor` inside an SVG image is black: the image doesn't inherit the page's colour.
- Clients may cap the rasterized size (the Apple renderer stops at 4096 device pixels a side, and refuses markup over 2 MB or sizes over 16384 px).

Writing them:

- `encodeURIComponent()` the markup, or percent-encode at least `%`, `#`, `<`, `>`, `"` and whitespace. Encoding `'`, `(` and `)` as well makes the URL safe in every context: an HTML attribute with either quote, an unquoted CSS `url()` and any JavaScript string.
- Use single quotes inside the SVG.
- A script may build a new SVG URL every frame for short animations. Clients cache decoded images in a byte-bounded cache keyed by the full URL.

`Examples/images` holds the demo pictures (`sunset`, `aurora`, `ocean`, `dunes`, `forest`, `map`, `logo`) as small SVG files. They replace the `app:` pictures the macOS app used to draw in code.

## `icon:` URLs

```
icon:<name>[?size=<px>][&color=<hex>][&weight=<weight>]
```

The icon set is a subset of [Phosphor Icons](https://phosphoricons.com) 2.1.1 (MIT), shipped in `Sources/TwidgetAssets`:

- `icons.json` is the manifest: format `twidget-icons`, `version` 1, the icon names, the variants, defaults, limits, the weight table and the `sf:` alias table. A client implements exactly the names in the manifest version it ships. Adding icons bumps `version`; names are never removed or redrawn within a version.
- `icons/<variant>/<name>.svg` are the unmodified Phosphor files, four variants per icon: `light`, `regular`, `bold` and `fill`. Every file is `<svg viewBox="0 0 256 256" fill="currentColor">` with one or two filled `<path>` elements and no strokes, so every client draws the same shape with any SVG or path renderer.
- `twa_icon_data.inc` is the same path data compiled into C++ by `tools/icons/generate.py`, for the portable resolver in `Sources/TwidgetAssets/twa_icons.cpp` (C API in `include/twidget_assets.h`). Android, iOS and desktop ports can compile that file as is, or read `icons.json` and the SVG files.

363 icons × 4 variants is about 600 KB of path data. The subset holds every icon an `sf:` alias points to plus a common UI set (arrows, carets, files, chat, media controls, weather, commerce, people, devices, code). The whole Phosphor set would be about 2.9 MB in the binary of every client for names nobody uses yet; the manifest can grow later.

### Grammar

- **name**: `[a-z0-9]+(-[a-z0-9]+)*`, at most 64 characters, one of the manifest's `icons`, optionally with the suffix `-fill`. `heart-fill` is the `fill` variant of `heart`. Matching is exact and case-sensitive; the scheme is case-insensitive.
- **query**: `&`-separated `key=value` pairs. `%XX` escapes in values are decoded. Unknown keys are ignored, and for a repeated key the last one wins. A `#fragment` is ignored.
- A URL longer than 512 characters is not parsed: it draws the placeholder at the default size and colour.

### Size

`size` is the edge of the square image box in CSS px. The 256-unit `viewBox` scales uniformly to fill the box, with no padding added: Phosphor's own padding (about 1/8 of the box) is part of the glyph.

- A decimal number: digits with at most one `.`, an optional leading `+`, at most 16 characters. No signs, exponents, units, `NaN` or `inf`.
- Clamped to 1…512 and rounded to 0.01 px.
- Missing or invalid: 20.

Like any image, the box can be resized with CSS or `width`/`height` attributes. Clients rasterize at `size` × the device scale (2 on the Apple renderer), so draw an icon at its final size for the sharpest result.

### Colour

`color` is 3, 4, 6 or 8 hex digits with an optional leading `#` (written `%23` in a URL): `rgb`, `rgba`, `rrggbb` or `rrggbbaa`, in sRGB. Every path is filled with that colour; alpha below 1 is a fill opacity. Missing or invalid: `404045`. There is no `currentColor` for icons, since an image can't inherit the page's colour.

### Weight

`weight` picks one of the four variants. Each variant has its own path data; weights are never emulated with stroke widths.

| `weight` | Variant |
|---|---|
| `ultralight`, `thin`, `light` | light |
| `regular`, `medium` (and missing or unknown) | regular |
| `semibold`, `bold`, `heavy`, `black` | bold |
| `fill` | fill |

A `-fill` name always draws the fill variant, whatever `weight` says.

`mode` (`multicolor` and friends) has no effect on `icon:`: icons are monochrome.

### Unknown names

A name that isn't in the manifest, or isn't valid at all (`icon:../x`, `icon:Heart`, `icon:${name}`), draws the `placeholder` icon with the requested size, colour and weight. It never fails, never falls back to anything else and never fetches anything.

### Canonical SVG

So that every client produces identical pixels from identical input, an icon is the SVG document

```
<svg xmlns="http://www.w3.org/2000/svg" width="S" height="S" viewBox="0 0 256 256"><path fill="#rrggbb" fill-opacity="A" d="…"/></svg>
```

with one `<path>` per path of the variant, `S` the size with at most two decimals and trailing zeros removed, and `fill-opacity` (at most three decimals) only when alpha is below 1. `twa::svg`, `twa::dataURL` and `twa::canonicalURL` produce it, its `data:` URL and the normalized `icon:` URL.

## Apple compatibility: `sf:` and `app:`

Older Apple-only messages use two schemes no other client can render:

- `sf:name?size=&color=&weight=&mode=` drew SF Symbols, which Apple licenses only for its own platforms.
- `app:name` drew seven pictures in code.

Rules for the format:

- New widgets must not use them.
- The Apple renderer keeps drawing `sf:` with SF Symbols when the host allows the scheme, and falls back to the alias table for names AppKit doesn't know (or before macOS 11). `LHImages.portableSymbols = true` (or `LH_PORTABLE_SYMBOLS=1`) resolves every `sf:` through the aliases, to compare with other clients.
- Other clients resolve `sf:` through `sfAliases` in `icons.json`: the SF name maps to an icon name, a `.fill` symbol maps to a `-fill` icon, `size` is multiplied by `sfSizeScale` (1.2, because an SF Symbol at point size N draws about 1.2 N px tall), `weight` maps through the weight table, and `mode=multicolor` uses the colour in `sfMulticolor` when one is listed. Unknown names draw the placeholder.
- `app:` stays a host scheme on Apple; other clients drop it.

Migrating a message is mechanical: `sf:heart.fill?size=20&color=ff2d55` becomes `icon:heart-fill?size=24&color=ff2d55` (alias, `.fill` → `-fill`, size × 1.2), and `app:sunset` becomes the `data:` URL of `Examples/images/sunset.svg`.

## twidget hosts and the validator

The twidget validator allows only `data:` images today. Until it lists `icon:` (the grammar above, with the length limit), a host expands `icon:` URLs into the canonical `data:image/svg+xml` URL before validation, with `twa_icon_data_url`. Both the WebView renderer and the native renderer then receive the same SVG, so icons look the same in both.

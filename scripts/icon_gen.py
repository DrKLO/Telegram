#!/usr/bin/env python3
"""Generates the custom launcher icon resources from tg-icon.png.

Called by scripts/apply-icon.sh (run that, not this file).

Nothing about the resource layout is hardcoded: the main resource directory is
found by locating mipmap-anydpi-v26/ic_launcher.xml, and every density / pixel
size is taken from the files that already exist there.

What is replaced (in the build workspace only, tg-icon.png is never modified):
  * mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml  (adaptive icon)
  * mipmap-<density>/custom_icon_foreground.png               (new adaptive foreground)
  * values/custom_icon_colors.xml                             (new adaptive background colour)
  * mipmap-<density>/ic_launcher.png, ic_launcher_round.png   (legacy icons, Android < 8)
  * drawable-<density>/ic_launcher_dr.webp                    (notification / call icon)
  * optionally the alternative icons icon_N_launcher*         (--alternates)
The upstream "monochrome" (themed icon) layer is dropped because it is Telegram's paper plane.
"""
import argparse
import glob
import hashlib
import json
import os
import re
import sys

try:
    from PIL import Image, ImageChops, ImageDraw, features
except ImportError:  # pragma: no cover
    print("apply-icon: ERROR: Pillow is required (pip install pillow)", file=sys.stderr)
    sys.exit(1)

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MIN_SIZE = 192            # smallest accepted source (xxxhdpi legacy icon is 192 px)
RECOMMENDED_SIZE = 512
ADAPTIVE_SAFE_SCALE = 72.0 / 108.0   # visible area of an adaptive icon: 72dp of the 108dp canvas


def fail(msg):
    print("apply-icon: ERROR: " + msg, file=sys.stderr)
    sys.exit(1)


def note(msg):
    print("apply-icon: " + msg)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


# ---------------------------------------------------------------- validation
def load_icon(path):
    if not os.path.isfile(path):
        fail("icon file not found: %s" % path)
    with open(path, "rb") as f:
        if f.read(8) != PNG_SIGNATURE:
            fail("%s is not a PNG file (bad signature)" % path)
    try:
        with Image.open(path) as probe:
            probe.verify()
        im = Image.open(path)
        im.load()
    except Exception as e:  # noqa: BLE001
        fail("%s is not a valid PNG: %s" % (path, e))
    if im.format != "PNG":
        fail("%s is not a PNG (detected %s)" % (path, im.format))
    w, h = im.size
    if min(w, h) < MIN_SIZE:
        fail("icon is %dx%d, minimum is %dx%d" % (w, h, MIN_SIZE, MIN_SIZE))
    if abs(w - h) > 0.02 * max(w, h):
        fail("icon must be square, got %dx%d (it would be distorted)" % (w, h))
    if min(w, h) < RECOMMENDED_SIZE:
        note("WARNING: icon is %dx%d, %dx%d or larger is recommended" % (w, h, RECOMMENDED_SIZE, RECOMMENDED_SIZE))
    return im.convert("RGBA")


# ------------------------------------------------------- structure detection
def find_main_res(root):
    hits = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "build", "node_modules", ".gradle", "jni")]
        if os.path.basename(dirpath) == "mipmap-anydpi-v26" and "ic_launcher.xml" in filenames:
            res = os.path.dirname(dirpath)
            if res.replace(os.sep, "/").endswith("src/main/res"):
                hits.append(res)
    if not hits:
        fail("could not find a src/main/res/mipmap-anydpi-v26/ic_launcher.xml under %s - "
             "the Telegram resource layout changed, update scripts/icon_gen.py" % root)
    if len(hits) > 1:
        fail("several candidate resource directories found, refusing to guess:\n  " + "\n  ".join(hits))
    return hits[0]


def png_size(path):
    with Image.open(path) as im:
        return im.size[0]


def densities(res, relpattern):
    """{dir_name: existing_pixel_size} for files like mipmap-*/ic_launcher.png."""
    out = {}
    for p in sorted(glob.glob(os.path.join(res, relpattern))):
        out[os.path.basename(os.path.dirname(p))] = png_size(p)
    return out


# ------------------------------------------------------------ image helpers
def fit(im, size, scale=1.0):
    """Scale `im` to size*scale and centre it on a transparent size x size canvas."""
    inner = max(1, int(round(size * scale)))
    resized = im.resize((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    off = (size - inner) // 2
    canvas.paste(resized, (off, off), resized)
    return canvas


def circle(im):
    """Apply an anti-aliased circular mask (legacy round launcher icon)."""
    size = im.size[0]
    big = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(big).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    mask = big.resize((size, size), Image.LANCZOS)
    out = im.copy()
    out.putalpha(ImageChops.multiply(im.getchannel("A"), mask))
    return out


def background_colour(im):
    """Median colour of the opaque border band; white if the border is mostly transparent."""
    w, h = im.size
    band = max(2, int(0.04 * w))
    px = im.load()
    pixels = []
    for y in range(h):
        for x in range(w):
            if x < band or y < band or x >= w - band or y >= h - band:
                r, g, b, a = px[x, y]
                if a >= 250:
                    pixels.append((r, g, b))
    total = 2 * band * (w + h - 2 * band)
    if len(pixels) < 0.2 * total:
        return "#FFFFFF"
    chans = [sorted(p[i] for p in pixels) for i in range(3)]
    mid = len(pixels) // 2
    return "#%02X%02X%02X" % (chans[0][mid], chans[1][mid], chans[2][mid])


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/custom_icon_background" />
    <foreground android:drawable="@mipmap/custom_icon_foreground" />
</adaptive-icon>
"""

COLORS_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="custom_icon_background">{colour}</color>
</resources>
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--icon", required=True, help="path to tg-icon.png")
    ap.add_argument("--root", required=True, help="source tree root")
    ap.add_argument("--res", help="resource directory override (default: auto-detected)")
    ap.add_argument("--report-dir", help="where to write icon-applied.json (default: <root>/build-reports)")
    ap.add_argument("--adaptive-mode", choices=("safe", "bleed"), default="safe",
                    help="safe: artwork scaled into the adaptive safe zone (default); bleed: artwork fills the whole canvas")
    ap.add_argument("--alternates", action="store_true",
                    help="also replace the alternative app icons (icon_2..icon_6) with the custom icon")
    ap.add_argument("--check-only", action="store_true", help="validate the PNG only")
    ap.add_argument("--dry-run", action="store_true", help="detect structure and print the plan, write nothing")
    args = ap.parse_args()

    icon = load_icon(args.icon)
    note("source icon OK: %s (%dx%d)" % (args.icon, icon.size[0], icon.size[1]))
    if args.check_only:
        return

    if not features.check("webp"):
        fail("this Pillow build has no WebP support (needed for ic_launcher_dr.webp)")

    res = args.res or find_main_res(args.root)
    res = os.path.abspath(res)
    if not os.path.isdir(res):
        fail("resource directory does not exist: %s" % res)
    note("resource directory: %s" % os.path.relpath(res, args.root))

    legacy = densities(res, "mipmap-*/ic_launcher.png")
    if not legacy:
        fail("no mipmap-*/ic_launcher.png found in %s - unexpected resource layout" % res)
    fg_existing = densities(res, "mipmap-*/icon_foreground.png")
    dr = densities(res, "drawable-*/ic_launcher_dr.webp")
    alt_names = sorted({re.sub(r"\.xml$", "", os.path.basename(p))
                        for p in glob.glob(os.path.join(res, "mipmap-anydpi-v26", "icon_*_launcher.xml"))})

    # Report what the original adaptive icon referenced (useful when upstream changes it).
    orig_xml = os.path.join(res, "mipmap-anydpi-v26", "ic_launcher.xml")
    with open(orig_xml, encoding="utf-8") as f:
        refs = re.findall(r'android:drawable="([^"]+)"', f.read())
    note("current adaptive icon references: %s" % (", ".join(refs) if refs else "(none found)"))

    scale = ADAPTIVE_SAFE_SCALE if args.adaptive_mode == "safe" else 1.0
    colour = background_colour(icon)
    note("adaptive mode: %s, background colour %s" % (args.adaptive_mode, colour))

    plan = []  # (path, kind, writer)

    def add(path, kind, writer):
        plan.append((path, kind, writer))

    def png_writer(img):
        return lambda p: img.save(p, "PNG", optimize=True)

    def webp_writer(img):
        return lambda p: img.save(p, "WEBP", lossless=True, quality=100, method=6)

    def text_writer(text):
        def w(p):
            with open(p, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
        return w

    for d, size in legacy.items():
        base = fit(icon, size)
        add(os.path.join(res, d, "ic_launcher.png"), "legacy", png_writer(base))
        add(os.path.join(res, d, "ic_launcher_round.png"), "legacy-round", png_writer(circle(base)))
        fg_size = fg_existing.get(d, int(round(size * 108 / 48)))
        add(os.path.join(res, d, "custom_icon_foreground.png"), "adaptive-foreground",
            png_writer(fit(icon, fg_size, scale)))
    for d, size in dr.items():
        add(os.path.join(res, d, "ic_launcher_dr.webp"), "notification", webp_writer(fit(icon, size)))
    v26 = os.path.join(res, "mipmap-anydpi-v26")
    add(os.path.join(v26, "ic_launcher.xml"), "adaptive-xml", text_writer(ADAPTIVE_XML))
    add(os.path.join(v26, "ic_launcher_round.xml"), "adaptive-xml", text_writer(ADAPTIVE_XML))
    add(os.path.join(res, "values", "custom_icon_colors.xml"), "colour", text_writer(COLORS_XML.format(colour=colour)))

    untouched = []
    if args.alternates:
        for name in alt_names:
            add(os.path.join(v26, name + ".xml"), "alternate-xml", text_writer(ADAPTIVE_XML))
            add(os.path.join(v26, name + "_round.xml"), "alternate-xml", text_writer(ADAPTIVE_XML))
            for d, size in legacy.items():
                for suffix in ("", "_round"):
                    existing = os.path.join(res, d, name + suffix + ".png")
                    if os.path.exists(existing):
                        s = png_size(existing)
                        base = fit(icon, s)
                        add(existing, "alternate-legacy", png_writer(circle(base) if suffix else base))
    else:
        untouched += ["mipmap-*/%s*" % n for n in alt_names]

    # other modules with their own launcher families (e.g. standalone "_sa" icons) are reported, not touched
    for p in sorted(glob.glob(os.path.join(args.root, "TMessagesProj_*", "src", "main", "res", "mipmap-anydpi-v26", "ic_launcher*.xml"))):
        untouched.append(os.path.relpath(p, args.root))

    if args.dry_run:
        for path, kind, _ in plan:
            note("would write [%s] %s" % (kind, os.path.relpath(path, args.root)))
        if untouched:
            note("left untouched: " + ", ".join(untouched))
        return

    written = []
    for path, kind, writer in plan:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        writer(path)
        if not os.path.getsize(path):
            fail("generated an empty file: %s" % path)
        written.append({"file": os.path.relpath(path, args.root).replace(os.sep, "/"), "kind": kind, "sha256": sha256(path)})

    report_dir = args.report_dir or os.path.join(args.root, "build-reports")
    os.makedirs(report_dir, exist_ok=True)
    report = {
        "source": os.path.relpath(args.icon, args.root).replace(os.sep, "/"),
        "source_sha256": sha256(args.icon),
        "source_size": list(icon.size),
        "adaptive_mode": args.adaptive_mode,
        "background_colour": colour,
        "resource_dir": os.path.relpath(res, args.root).replace(os.sep, "/"),
        "alternates_replaced": bool(args.alternates),
        "left_untouched": untouched,
        "files": written,
    }
    with open(os.path.join(report_dir, "icon-applied.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, sort_keys=True)
        f.write("\n")
    note("wrote %d resource files (report: %s)" % (len(written), os.path.join(os.path.relpath(report_dir, args.root), "icon-applied.json")))
    if untouched:
        note("left untouched: " + ", ".join(untouched))


if __name__ == "__main__":
    main()

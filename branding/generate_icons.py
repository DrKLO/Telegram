#!/usr/bin/env python3
"""Regenerate MVGram launcher and category icon assets from editable SVG sources.
Run from any working directory: python3 branding/generate_icons.py
Requires CairoSVG and Pillow. This script does not invoke Gradle or build the app.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from io import BytesIO
import cairosvg
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "TMessagesProj/src/main/res"
BRAND = ROOT / "branding"
SVG_NS = "http://www.w3.org/2000/svg"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)
BLACK = "#000000"
WHITE = "#FFFFFF"

# Initial original category glyphs. Each item becomes an editable SVG master
# once, then the generator reads that SVG to create a native Android VectorDrawable.
# Existing SVG masters are never overwritten, so edits to them survive regeneration.
ICON_SEEDS = {
    "chats": [
        ("M14 9H58C61.3 9 64 11.7 64 15V42C64 45.3 61.3 48 58 48H37L22 61V48H14C10.7 48 8 45.3 8 42V15C8 11.7 10.7 9 14 9Z", "#2F74E8", None, None, None),
        ("M20 27A3 3 0 1 0 26 27A3 3 0 1 0 20 27Z M33 27A3 3 0 1 0 39 27A3 3 0 1 0 33 27Z M46 27A3 3 0 1 0 52 27A3 3 0 1 0 46 27Z", "#FFFFFF", None, None, None),
    ],
    "contacts": [
        ("M36 9A10 10 0 1 0 36 29A10 10 0 1 0 36 9Z", "#168B7A", None, None, None),
        ("M13 59C14 47 22 40 36 40C50 40 58 47 59 59Z", "#168B7A", None, None, None),
        ("M55 40A9 9 0 1 0 55 58A9 9 0 1 0 55 40Z", "#4569D4", None, None, None),
        ("M54 44V54 M49 49H59", "none", "#FFFFFF", "2.8", None),
    ],
    "calls": [
        ("M21 10C18 10 16 12 16 15C16 37 34 56 56 56C59 56 61 54 61 51V43C61 41 60 40 58 39L49 37C47 37 46 38 45 40L42 44C34 41 29 36 26 28L30 25C32 24 33 23 32 21L30 13C30 11 29 10 27 10Z", "#6655C8", None, None, None),
        ("M48 12A8 8 0 0 1 60 24 M48 20A4 4 0 0 1 52 24", "none", "#E56A62", "3.5", None),
    ],
    "groups": [
        ("M36 11A8 8 0 1 0 36 27A8 8 0 1 0 36 11Z M15 18A6 6 0 1 0 15 30A6 6 0 1 0 15 18Z M57 18A6 6 0 1 0 57 30A6 6 0 1 0 57 18Z", "#168B7A", None, None, None),
        ("M21 59C22 47 27 40 36 40C45 40 50 47 51 59Z", "#6655C8", None, None, None),
        ("M4 57C5 47 9 41 16 41C21 41 24 44 26 48C23 51 22 55 22 59H6Z M50 48C52 44 55 41 60 41C67 41 71 47 72 57L66 59H50Z", "#2F74E8", None, None, None),
    ],
    "settings": [
        ("M31 6H41L43 13C45 14 47 15 49 17L56 15L61 24L56 29C57 32 57 35 56 38L61 43L56 52L49 50C47 52 45 53 43 54L41 61H31L29 54C27 53 25 52 23 50L16 52L11 43L16 38C15 35 15 32 16 29L11 24L16 15L23 17C25 15 27 14 29 13Z M36 25A11 11 0 1 0 36 47A11 11 0 1 0 36 25Z", "#52677A", None, None, "evenodd"),
        ("M36 31A5 5 0 1 0 36 41A5 5 0 1 0 36 31Z", "#1A9E8B", None, None, None),
    ],
    "profile": [
        ("M36 9A11 11 0 1 0 36 31A11 11 0 1 0 36 9Z", "#485FC2", None, None, None),
        ("M13 60C14 47 22 40 36 40C50 40 58 47 59 60Z", "#485FC2", None, None, None),
        ("M56 42A8 8 0 1 0 56 58A8 8 0 1 0 56 42Z", "#E56A62", None, None, None),
        ("M56 46V54 M52 50H60", "none", "#FFFFFF", "2.5", None),
    ],
    "search": [
        ("M33 11A19 19 0 1 0 33 49A19 19 0 1 0 33 11Z", "none", "#216BAA", "7", None),
        ("M47 47L62 62", "none", "#1A9E8B", "7", None),
    ],
    "navigation": [
        ("M36 7C23 7 13 17 13 30C13 47 36 66 36 66S59 47 59 30C59 17 49 7 36 7Z", "#D95B59", None, None, None),
        ("M36 22A8 8 0 1 0 36 38A8 8 0 1 0 36 22Z", "#168B7A", None, None, None),
    ],
    "attachments": [
        ("M45 19L28 36A9 9 0 0 0 41 49L56 33A13 13 0 0 0 38 15L20 33A19 19 0 0 0 47 60L62 45", "none", "#C87532", "6.5", None),
        ("M39 26L26 39A4 4 0 0 0 32 45L45 32", "none", "#6655C8", "4", None),
    ],
    "camera": [
        ("M14 20H24L29 13H44L49 20H58C61 20 63 22 63 25V53C63 56 61 58 58 58H14C11 58 9 56 9 53V25C9 22 11 20 14 20Z", "#286DB7", None, None, None),
        ("M36 25A12 12 0 1 0 36 49A12 12 0 1 0 36 25Z", "#1A9E8B", None, None, None),
        ("M36 30A7 7 0 1 0 36 44A7 7 0 1 0 36 30Z", "#FFFFFF", None, None, None),
        ("M53 25A3 3 0 1 0 53 31A3 3 0 1 0 53 25Z", "#E5A235", None, None, None),
    ],
    "gallery": [
        ("M12 13H48C51 13 53 15 53 18V47H17C14 47 12 45 12 42Z", "#6655C8", None, None, None),
        ("M20 25H57C60 25 62 27 62 30V56H25C22 56 20 54 20 51Z", "#168B7A", None, None, None),
        ("M26 49L36 38L43 45L49 39L59 50V54H26Z", "#E5A235", None, None, None),
        ("M21 20A4 4 0 1 0 21 28A4 4 0 1 0 21 20Z", "#E56A62", None, None, None),
    ],
    "files": [
        ("M8 18C8 15 10 13 13 13H30L36 20H59C62 20 64 22 64 25V54C64 57 62 59 59 59H13C10 59 8 57 8 54Z", "#D99A2B", None, None, None),
        ("M8 26H64V54C64 57 62 59 59 59H13C10 59 8 57 8 54Z", "#E6B64A", None, None, None),
        ("M27 34H46V50H27Z M31 39H42 M31 44H42", "#FFFFFF", None, None, None),
    ],
    "notifications": [
        ("M36 9C27 9 22 16 22 26V35C22 40 19 44 15 48H57C53 44 50 40 50 35V26C50 16 45 9 36 9Z", "#6655C8", None, None, None),
        ("M29 53C30 59 33 62 36 62C39 62 42 59 43 53Z", "#E5A235", None, None, None),
        ("M53 10A7 7 0 1 0 53 24A7 7 0 1 0 53 10Z", "#168B7A", None, None, None),
    ],
    "messaging": [
        ("M13 12H59C62 12 64 14 64 17V44C64 47 62 49 59 49H39L24 61V49H13C10 49 8 47 8 44V17C8 14 10 12 13 12Z", "#168B7A", None, None, None),
        ("M18 24H53 M18 32H48 M18 40H39", "none", "#FFFFFF", "3.5", None),
    ],
    "music": [
        ("M42 12V46A10 10 0 1 1 36 37V19L60 13V40A10 10 0 1 1 54 31V22Z", "#6655C8", None, None, None),
        ("M23 51A7 7 0 1 0 23 65A7 7 0 1 0 23 51Z M53 45A7 7 0 1 0 53 59A7 7 0 1 0 53 45Z", "#1A9E8B", None, None, None),
    ],
    "video": [
        ("M9 18C9 15 11 13 14 13H44C47 13 49 15 49 18V54C49 57 47 59 44 59H14C11 59 9 57 9 54Z", "#286DB7", None, None, None),
        ("M49 27L64 19V53L49 45Z", "#1A9E8B", None, None, None),
        ("M24 25L39 36L24 47Z", "#E5A235", None, None, None),
    ],
    "archive": [
        ("M12 14H60V25H12Z M16 27H56V58H16Z", "#52677A", None, None, None),
        ("M28 33H44V38H28Z M31 39H41V52H31Z", "#1A9E8B", None, None, None),
    ],
    "privacy": [
        ("M36 8L58 17V33C58 47 48 57 36 64C24 57 14 47 14 33V17Z", "#286DB7", None, None, None),
        ("M28 32V27A8 8 0 0 1 44 27V32 M27 32H45V47H27Z", "none", "#E5A235", "4", None),
        ("M36 37V42", "none", "#FFFFFF", "3", None),
    ],
}

DENSITY = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
LAUNCHER = {k: int(48*v) for k, v in DENSITY.items()}
FOREGROUND = {k: int(108*v) for k, v in DENSITY.items()}
ICON_FILE = re.compile(r"^(?:ic_launcher(?:_round)?|icon(?:_[2-6])?_(?:launcher(?:_round)?|foreground(?:_round)?(?:_sa)?))\.png$")
BG_FILE = re.compile(r"^icon(?:_[2-6])?_background(?:_round|_clip(?:_round)?|_sa)?\.png$")


def vector_from_svg(svg_path, resource_name, width_dp, height_dp):
    root = ET.parse(svg_path).getroot()
    viewbox = root.attrib.get("viewBox", "0 0 72 72").split()
    vw, vh = viewbox[2], viewbox[3]
    out = [f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="{ANDROID_NS}" android:width="{width_dp}dp" android:height="{height_dp}dp" android:viewportWidth="{vw}" android:viewportHeight="{vh}">''']
    for path in root.findall(f"{{{SVG_NS}}}path"):
        d = path.attrib["d"]
        fill = path.attrib.get("fill", "#00000000")
        if fill == "none":
            fill = "#00000000"
        attrs = [f'android:fillColor="{fill}"', f'android:pathData="{d}"']
        if path.attrib.get("fill-rule") == "evenodd":
            attrs.append('android:fillType="evenOdd"')
        if "stroke" in path.attrib and path.attrib["stroke"] != "none":
            attrs.append(f'android:strokeColor="{path.attrib["stroke"]}"')
            attrs.append(f'android:strokeWidth="{path.attrib.get("stroke-width", "1")}"')
            if "stroke-linecap" in path.attrib:
                attrs.append(f'android:strokeLineCap="{path.attrib["stroke-linecap"]}"')
            if "stroke-linejoin" in path.attrib:
                attrs.append(f'android:strokeLineJoin="{path.attrib["stroke-linejoin"]}"')
        out.append("    <path " + " ".join(attrs) + " />")
    out.append("</vector>\n")
    return "\n".join(out)


def seed_internal_svg_files():
    icon_dir = BRAND / "internal-icons"
    icon_dir.mkdir(parents=True, exist_ok=True)
    for name, shapes in ICON_SEEDS.items():
        path = icon_dir / f"mvgram_{name}.svg"
        if path.exists():
            continue
        elems = []
        for d, fill, stroke, stroke_width, fill_rule in shapes:
            attrs = [f'd="{d}"', f'fill="{fill}"']
            if fill_rule:
                attrs.append(f'fill-rule="{fill_rule}"')
            if stroke:
                attrs.extend([f'stroke="{stroke}"', f'stroke-width="{stroke_width}"', 'stroke-linecap="round"', 'stroke-linejoin="round"'])
            elems.append("  <path " + " ".join(attrs) + "/>")
        doc = '<svg xmlns="http://www.w3.org/2000/svg" width="72" height="72" viewBox="0 0 72 72">\n' + "\n".join(elems) + '\n</svg>\n'
        path.write_text(doc, encoding="utf-8")


# Regenerate raster launcher icons using the editable primary and foreground SVGs.
master_path = BRAND / "mvgram-icon.svg"
foreground_path = BRAND / "mvgram-foreground.svg"
master = master_path.read_bytes()
foreground = foreground_path.read_bytes()
fg_root = ET.parse(foreground_path).getroot()
fg_paths = fg_root.findall(f"{{{SVG_NS}}}path")
plane = next(p for p in fg_paths if p.attrib.get("fill") == BLACK)

for folder in sorted(PROJECT.glob("mipmap-*")):
    density = folder.name.removeprefix("mipmap-")
    if density not in DENSITY:
        continue
    for dest in folder.glob("*.png"):
        name = dest.name
        if ICON_FILE.match(name):
            px = FOREGROUND[density] if "foreground" in name else LAUNCHER[density]
            svg = foreground if "foreground" in name else master
            out = Image.open(BytesIO(cairosvg.svg2png(bytestring=svg, output_width=px, output_height=px))).convert("RGBA")
            if "round" in name:
                mask = Image.new("L", (px, px), 0)
                ImageDraw.Draw(mask).ellipse((0, 0, px-1, px-1), fill=255)
                out.putalpha(Image.composite(out.getchannel("A"), Image.new("L", (px, px), 0), mask))
            out.save(dest, format="PNG", optimize=True)
        elif BG_FILE.match(name):
            px = FOREGROUND[density]
            if "clip" in name:
                Image.new("RGBA", (px, px), (0, 0, 0, 0)).save(dest, format="PNG", optimize=True)
            else:
                Image.new("RGBA", (px, px), (255, 255, 255, 255)).save(dest, format="PNG", optimize=True)

# Standalone/SMS distribution keeps its independently packaged launcher assets.
standalone = ROOT / "TMessagesProj_AppStandalone/src/main/res"
for folder in sorted(standalone.glob("mipmap-*")):
    density = folder.name.removeprefix("mipmap-")
    if density not in DENSITY:
        continue
    for dest in folder.glob("icon_[2-6]_launcher_sa.png"):
        image = Image.open(BytesIO(cairosvg.svg2png(bytestring=master, output_width=LAUNCHER[density], output_height=LAUNCHER[density]))).convert("RGBA")
        image.save(dest, format="PNG", optimize=True)

# Existing draggable launcher previews use the same white/black identity.
for folder in sorted(PROJECT.glob("drawable-*dpi")):
    dest = folder / "ic_launcher_dr.webp"
    if dest.exists():
        d = folder.name.removeprefix("drawable-").removesuffix("dpi")
        density = {"m":"mdpi", "h":"hdpi", "x":"xhdpi", "xx":"xxhdpi", "xxx":"xxxhdpi"}.get(d)
        if density:
            image = Image.open(BytesIO(cairosvg.svg2png(bytestring=master, output_width=LAUNCHER[density], output_height=LAUNCHER[density]))).convert("RGBA")
            image.save(dest, format="WEBP", quality=100, lossless=True)

# Adaptive-icon layer backgrounds are pure white; clipping masks remain transparent.
for path in (PROJECT / "drawable").glob("icon*background*.xml"):
    if re.fullmatch(r"icon(?:_[2-6])?_background(?:_round|_sa)?", path.stem):
        path.write_text(f'''<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="{ANDROID_NS}" android:shape="rectangle">\n    <solid android:color="{WHITE}" />\n</shape>\n''', encoding="utf-8")

# Rebuild app monochrome icon, splash, and system notification glyph from the plane master.
mono = f'''<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="{ANDROID_NS}" android:width="108dp" android:height="108dp" android:viewportWidth="1024" android:viewportHeight="1024">\n    <path android:fillColor="{BLACK}" android:pathData="{plane.attrib['d']}" />\n</vector>\n'''
(PROJECT / "drawable/mvgram_monochrome_icon.xml").write_text(mono, encoding="utf-8")
(PROJECT / "drawable/mvgram_splash_320.xml").write_text(vector_from_svg(foreground_path, "splash", 320, 320), encoding="utf-8")
notification = f'''<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="{ANDROID_NS}" android:width="24dp" android:height="24dp" android:viewportWidth="1024" android:viewportHeight="1024">\n    <path android:fillColor="{WHITE}" android:pathData="{plane.attrib['d']}" />\n</vector>\n'''
(PROJECT / "drawable/mvgram_notification.xml").write_text(notification, encoding="utf-8")
for style_file in (PROJECT / "values-night/styles.xml", PROJECT / "values-v31/styles.xml"):
    if style_file.exists():
        text = style_file.read_text(encoding="utf-8")
        text = re.sub(r'(<item name="android:windowSplashScreenBackground">).*?(</item>)', rf'\1{WHITE}\2', text)
        style_file.write_text(text, encoding="utf-8")

# Generate a coordinated original, transparent, color-category vector set.
seed_internal_svg_files()
for svg_path in sorted((BRAND / "internal-icons").glob("mvgram_*.svg")):
    name = svg_path.stem
    ET.parse(svg_path)
    (PROJECT / "drawable" / f"{name}.xml").write_text(vector_from_svg(svg_path, name, 24, 24), encoding="utf-8")

# Preserve display names and selected app-only localized branding strings.
for path in sorted(PROJECT.glob("values*/strings.xml")):
    text = path.read_text(encoding="utf-8")
    text = re.sub(r'(<string\s+name="AppName">).*?(</string>)', r'\1MVGram\2', text)
    text = re.sub(r'(<string\s+name="AppNameBeta">).*?(</string>)', r'\1MVGram Beta\2', text)
    def version_brand(m):
        return m.group(1) + re.sub(r"(?i)telegram|تيليجرام|텔레그램", "MVGram", m.group(2), count=1) + m.group(3)
    text = re.sub(r'(<string\s+name="TelegramVersion">)(.*?)(</string>)', version_brand, text)
    def language_brand(m):
        body = re.sub(r"(?i)telegram", "MVGram", m.group(2), count=1)
        body = body.replace("تيليجرام", "MVGram").replace("텔레그램", "MVGram")
        return m.group(1) + body + m.group(3)
    text = re.sub(r'(<string\s+name="LanguageUnknownCustomAlert">)(.*?)(</string>)', language_brand, text)
    path.write_text(text, encoding="utf-8")

# APP_PACKAGE is shared by the standard, Huawei, standalone, and test variants.
props = ROOT / "gradle.properties"
ptext = props.read_text(encoding="utf-8")
ptext, n = re.subn(r"(?m)^APP_PACKAGE=.*$", "APP_PACKAGE=com.mvgram.messenger", ptext)
if n != 1:
    raise RuntimeError(f"Expected one APP_PACKAGE setting; found {n}")
props.write_text(ptext, encoding="utf-8")
print(f"Generated launcher assets and {len(ICON_SEEDS)} editable internal-category SVG/Android vector icons.")

#!/usr/bin/env python3
import argparse
import json
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "Sources", "TwidgetAssets")
MANIFEST = os.path.join(ASSETS, "icons.json")
ICONS = os.path.join(ASSETS, "icons")
OUTPUT = os.path.join(ASSETS, "twa_icon_data.inc")

SVG = re.compile(r'^<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" fill="currentColor">((?:<path d="[0-9A-Za-z.,\- ]+"/>)+)</svg>\s*$')
PATH = re.compile(r'<path d="([0-9A-Za-z.,\- ]+)"/>')
NAME = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
SYMBOL = re.compile(r"^[a-z0-9]+(\.[a-z0-9]+)*$")
HEX = re.compile(r"^[0-9a-f]{6}$")
SUFFIX = {"light": "-light", "regular": "", "bold": "-bold", "fill": "-fill"}


def load_manifest():
    with open(MANIFEST, encoding="utf-8") as f:
        manifest = json.load(f)
    if manifest.get("format") != "twidget-icons":
        sys.exit("icons.json: unexpected format")
    if manifest["variants"] != ["light", "regular", "bold", "fill"]:
        sys.exit("icons.json: variants must be light, regular, bold, fill")
    icons = manifest["icons"]
    if icons != sorted(set(icons)):
        sys.exit("icons.json: icons must be sorted and unique")
    for name in icons:
        if not NAME.match(name) or name.endswith("-fill") or len(name) > manifest["limits"]["maxNameLength"]:
            sys.exit(f"icons.json: bad icon name {name}")
    if manifest["placeholder"] not in icons:
        sys.exit("icons.json: placeholder is not an icon")
    for sf, icon in manifest["sfAliases"].items():
        base = icon[:-5] if icon.endswith("-fill") else icon
        if not SYMBOL.match(sf) or base not in icons:
            sys.exit(f"icons.json: bad alias {sf} -> {icon}")
    for sf, color in manifest["sfMulticolor"].items():
        if sf not in manifest["sfAliases"] or not HEX.match(color):
            sys.exit(f"icons.json: bad multicolor entry {sf}")
    if not HEX.match(manifest["defaults"]["color"]):
        sys.exit("icons.json: bad default color")
    for weight, variant in manifest["weights"].items():
        if variant not in manifest["variants"]:
            sys.exit(f"icons.json: bad weight {weight}")
    return manifest


def vendor(manifest, package):
    assets = os.path.join(package, "assets")
    if os.path.isdir(ICONS):
        shutil.rmtree(ICONS)
    for variant in manifest["variants"]:
        os.makedirs(os.path.join(ICONS, variant))
        for name in manifest["icons"]:
            shutil.copyfile(os.path.join(assets, variant, name + SUFFIX[variant] + ".svg"), os.path.join(ICONS, variant, name + ".svg"))
    shutil.copyfile(os.path.join(package, "LICENSE"), os.path.join(ICONS, "LICENSE"))


def path_data(variant, name):
    with open(os.path.join(ICONS, variant, name + ".svg"), encoding="utf-8") as f:
        match = SVG.match(f.read())
    if not match:
        sys.exit(f"{variant}/{name}.svg is not a plain Phosphor icon")
    return "|".join(PATH.findall(match.group(1)))


def literal(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def generate(manifest):
    out = []
    limits = manifest["limits"]
    defaults = manifest["defaults"]
    out.append(f"static const int kManifestVersion = {int(manifest['version'])};")
    out.append(f"static const float kDefaultSize = {float(defaults['size'])}f;")
    out.append(f"static const uint32_t kDefaultColor = 0x{defaults['color']}ffu;")
    out.append(f"static const float kMinSize = {float(limits['minSize'])}f;")
    out.append(f"static const float kMaxSize = {float(limits['maxSize'])}f;")
    out.append(f"static const size_t kMaxURLLength = {int(limits['maxURLLength'])};")
    out.append(f"static const size_t kMaxNameLength = {int(limits['maxNameLength'])};")
    out.append(f"static const float kSymbolSizeScale = {float(manifest['sfSizeScale'])}f;")
    out.append(f"static const char* const kPlaceholder = {literal(manifest['placeholder'])};")
    out.append(f"static const Variant kDefaultVariant = Variant::{defaults['weight']};")
    out.append("static const WeightRecord kWeights[] = {")
    for weight, variant in sorted(manifest["weights"].items()):
        out.append(f"    {{{literal(weight)}, Variant::{variant}}},")
    out.append("};")
    out.append("static const IconRecord kIcons[] = {")
    for name in manifest["icons"]:
        paths = ", ".join(literal(path_data(v, name)) for v in manifest["variants"])
        out.append(f"    {{{literal(name)}, {{{paths}}}}},")
    out.append("};")
    out.append("static const AliasRecord kSymbolAliases[] = {")
    for sf, icon in sorted(manifest["sfAliases"].items()):
        color = manifest["sfMulticolor"].get(sf)
        tint = f"0x{color}ffu" if color else "0"
        out.append(f"    {{{literal(sf)}, {literal(icon)}, {tint}}},")
    out.append("};")
    return "\n".join(out) + "\n"


def main():
    parser = argparse.ArgumentParser(description="Vendors Phosphor icons named in icons.json and regenerates twa_icon_data.inc")
    parser.add_argument("--phosphor", help="an unpacked @phosphor-icons/core package to copy the SVG files from")
    parser.add_argument("--check", action="store_true", help="fail if twa_icon_data.inc is out of date")
    args = parser.parse_args()
    manifest = load_manifest()
    if args.phosphor:
        vendor(manifest, args.phosphor)
    text = generate(manifest)
    if args.check:
        with open(OUTPUT, encoding="utf-8") as f:
            if f.read() != text:
                sys.exit("twa_icon_data.inc is out of date; run tools/icons/generate.py")
        return
    with open(OUTPUT, "w", encoding="utf-8") as f:
        f.write(text)


if __name__ == "__main__":
    main()

# MVGram brand artwork

- `mvgram-icon.svg` is the primary 1024×1024 logo: a pure-white square with a centered black paper-plane mark.
- `mvgram-foreground.svg` is the transparent adaptive-icon foreground version of the same mark.
- `internal-icons/` holds original, editable category glyphs.
- `generate_icons.py` regenerates Android density-specific launcher PNG/WebP assets, adaptive background/foreground resources, monochrome and splash vectors, the white notification glyph, and the internal Android vectors. It requires CairoSVG and Pillow and does not invoke Gradle or build an APK/AAB.

The primary mark is deliberately monochrome. The internal category glyphs use a restrained category palette and remain separate from runtime-tinted interface controls.

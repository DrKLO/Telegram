# EPK3 version 3

All integers are little endian. No global compression. Images and index maps are 64×64, row-major. Emoji records precede internal mask records; keys are strictly increasing within each section. The public loader returns a complete Bitmap; none of these storage details are public API.

## Header: 32 bytes

| Offset | Type | Value |
|---:|---|---|
| 0 | char[4] | EPK3 |
| 4 | u16 | Version 3 |
| 6 | u16 | Record size 20 |
| 8 | u32 | Width 64 |
| 12 | u32 | Height 64 |
| 16 | u32 | Total record count |
| 20 | u32 | Emoji record count |
| 24 | u32 | File size |
| 28 | u32 | Reserved, zero |

## Record: 20 bytes

| Offset | Type | Meaning |
|---:|---|---|
| 0 | u16 | Emoji key `x*4096+y`, or internal mask ID |
| 2 | u16 | Internal mask ID, or 65535 |
| 4 | u32 | Absolute payload offset |
| 8 | u16 | Payload length in bytes |
| 10 | u16 | First parent record index, or 65535 |
| 12 | u16 | Second parent record index, or 65535 |
| 14 | u16 | Palette size 1…256; zero for whole images |
| 16 | u8 | Codec |
| 17 | u8 | Prediction |
| 18 | u8 | Low 7 bits: palette channels 0, 1, 3 or 4; bit 7: compressed palette |
| 19 | u8 | Flags, described below |

Parent values are indices in the entire record table, not emoji keys or mask IDs. A parent must be an independent indexed record: codec 0 or 1, prediction 0, no parents. Dependency chains are forbidden. A record may reference the same root for both halves, with independent transforms and remapping tables. Internal mask records cannot themselves reference a mask.

Flags:

- Bit 0: palette components packed into seven bits.
- Bits 1…3: transform of the first parent map.
- Bits 4…6: transform of the second parent map.
- Bit 7: selected with the visual quality gate; informational to the runtime.

Whole-image records have no palette, no parents and prediction 0. Their only permitted flag is bit 7. Unused transform fields written by the builder are zero.

## Palettes

Indexed payloads start with their palette, optionally compressed as described below. Channels 0 means implicit opaque gray: `palette[i] = (i,i,i,255)`, palette size must be 256, no stored bytes. Channels 1 stores opaque grayscale. Channels 3 stores RGB with alpha 255. Channels 4 stores RGBA, unpremultiplied.

If flag bit 0 is clear, components are ordinary bytes. Otherwise each component `v` is representable as `v = (q << 1) | (q >> 6)` for a seven-bit `q`. Store q as a continuous LSB-first bitstream; pad unused final bits with zero. Stored palette length is `ceil(colors * channels * 7 / 8)`. This packing itself is reversible, without extra posterization.

If bit 7 of the channel byte is set, the stored palette prefix is `u16(compressedLength)` followed by a raw DEFLATE stream of exactly compressedLength bytes. Its output is the ordinary stored palette representation described above, including seven-bit packing when flag bit 0 is set. The expected output length is therefore either `colors * channels` or `ceil(colors * channels * 7 / 8)`. No dictionary, zlib header or checksum is used. Channel count zero cannot have a compressed palette.

This palette stream is independent of the index/residual stream. It is inflated only when the palette is actually used for color expansion; loading a parent index map skips its palette without inflating it. The same Inflater and scratch arrays are reused.

## Codecs

| Codec | Payload after palette |
|---:|---|
| 0 | Raw DEFLATE, without zlib header/checksum; decoded bytes are concatenated LUTs, then 4096 residual/index bytes |
| 1 | Concatenated uncompressed LUTs, then a VP8L chunk payload; decode to an opaque grayscale index/residual image |
| 2 | Whole RGBA VP8L chunk payload; no palette or prediction |
| 3 | Complete standard PNG or WebP file; no palette or prediction |
| 4 | No stream: payload contains only the palette; prediction must be 4 |

For codecs 1 and 2, the 20-byte RIFF/WEBP/VP8L wrapper and final RIFF padding byte are omitted. Before BitmapFactory decoding, rebuild `RIFF`, `u32(12 + payloadLength + padding)`, `WEBPVP8L`, `u32(payloadLength)`, payload, and a zero padding byte if payloadLength is odd. Encoded stream bytes are otherwise unchanged. For codec 1 the LUTs are not included in payloadLength here.

Codec 3 stores the entire image file unchanged, including its container. The current pack uses it for one original PNG and 16 lossy WebP images. Codec 4 requires the same palette size as its parent; its palette may contain different colors.

## Prediction and geometry

| Prediction | Reconstruction |
|---:|---|
| 0 | No LUTs or parents; decoded byte is the palette index |
| 1 | `index = residual XOR lut[parentIndex]` |
| 2 | `index = (residual + lut[parentIndex]) & 255` |
| 3 | Same addition, first LUT/parent for destination x < 32, second LUT/parent for x ≥ 32 |
| 4 | Copy indices from the transformed parent; no residual stream and no LUT |
| 5 | Addition as in prediction 3; vertical split at the stored column |
| 6 | Addition as in prediction 3; horizontal split at the stored row |

Predictions 5 and 6 store one cut byte, in the range 1…63, immediately after the palette prefix and before the codec payload. This byte is outside both compressed streams. Prediction 3 stores no cut byte and keeps its fixed split at column 32. Prediction 6 samples parent A above the cut and parent B below it; prediction 5 samples A to the left and B to the right.

Each LUT contains exactly one byte per palette entry of its parent. For two halves, store the first LUT followed by the second. All split positions are in destination coordinates. There is no scaling, blending or positional offset.

A three-bit transform maps each destination pixel to the source position in the parent map:

```text
x = destinationIndex & 63
y = destinationIndex >> 6
if transform & 1: x = 63 - x
if transform & 2: y = 63 - y
if transform & 4: swap(x, y)
sourceIndex = y * 64 + x
```

Thus transform 1 is a horizontal reflection and transform 2 is a vertical reflection. Transform 4 transposes the square. The eight combinations cover the symmetries of the square. They affect parent sampling only, not the current residual stream or palette. No transformed Bitmap needs to be allocated.

The restored index must be smaller than the current palette size. Final palette expansion produces straight ARGB components.

## Complete emoji reconstruction

`getEmoji(x, y)` privately looks up the emoji key and decodes its color record. If an internal mask is present, the loader decodes that grayscale record and combines alpha before creating the final Bitmap:

```text
finalAlpha = (colorAlpha * maskGray + 127) / 255  // integer division
```

RGB components stay straight until Bitmap creation. Android Bitmap creation performs its normal premultiplication. For full images used with a mask, decoding requests unpremultiplied pixels first. A native full image with no separate mask may be returned directly as a premultiplied Bitmap.

The caller receives one complete ARGB_8888 Bitmap and owns it. The loader does not cache the returned Bitmap. Internal mask IDs and mask accessors are not exposed. The root-map cache is independent of Bitmap ownership and bounded to eight 4096-byte maps. All decode operations on one loader are synchronized.

## Validation and integrity

The loader checks the header, record bounds, references, independent roots, palette indices, dimensions and stream lengths. It does not decompress all images at startup. The pack has no per-record checksum and is intended to be shipped as a trusted application asset. The independent verifier and supplied SHA-256 hashes establish build integrity.

Version 3 adds variable horizontal/vertical splits (predictions 5/6) and optional palette compression (bit 7 of the channel byte). Header and record sizes remain unchanged. The supplied Java reader supports both versions 2 and 3. The old v2 reader cannot read v3; replace the pack and loader together. The public getEmoji contract continues to return a complete Bitmap.

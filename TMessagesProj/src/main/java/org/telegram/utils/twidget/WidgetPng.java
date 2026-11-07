package org.telegram.utils.twidget;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A PNG broken mid-stream, as Chromium draws it: the rows before the first bad filter byte or the end of
 * the data stay, the rest is transparent (upstream twidget LHContainer.mm, PNGIntactRows). BitmapFactory
 * refuses the whole image instead.
 */
final class WidgetPng {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private WidgetPng() {
    }

    /** The image with its intact rows, or null when `bytes` is not a broken PNG (decode it as it is). */
    static Bitmap decodeBroken(byte[] bytes) {
        final int length = bytes.length;
        if (length < 33 || !startsWith(bytes, 0, SIGNATURE) || !type(bytes, 12, "IHDR") || be32(bytes, 8) < 13) return null;
        final long width = be32(bytes, 16), height = be32(bytes, 20);
        final int depth = bytes[24] & 0xFF, colorType = bytes[25] & 0xFF, interlace = bytes[28] & 0xFF;
        final int channels = colorType == 0 || colorType == 3 ? 1 : colorType == 2 ? 3 : colorType == 4 ? 2 : colorType == 6 ? 4 : 0;
        if (channels == 0 || interlace != 0 || width <= 0 || height <= 0 || width * height > 4 * 1024 * 1024) return null;

        // Suspect: an IDAT whose CRC fails, or no IEND.
        boolean suspect = true;
        CRC32 crc = new CRC32();
        for (int at = 8; at + 12 <= length; ) {
            final long size = be32(bytes, at);
            if (size > length - at - 12) break;
            if (type(bytes, at + 4, "IEND")) {
                suspect = false;
                break;
            }
            if (type(bytes, at + 4, "IDAT")) {
                crc.reset();
                crc.update(bytes, at + 4, (int) size + 4);
                if (crc.getValue() != be32(bytes, at + 8 + (int) size)) break;
            }
            at += (int) size + 12;
        }
        if (!suspect) return null;

        // The rows the stream gives whole, each led by a valid filter byte (0-4).
        final int stride = (int) ((width * channels * depth + 7) / 8 + 1);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        ByteArrayOutputStream ancillary = new ByteArrayOutputStream();
        // Raw deflate past the 2-byte zlib header: a bad Adler-32 at the end must not take the rows with it.
        Inflater inflater = new Inflater(true);
        boolean header = true;
        byte[] chunk = new byte[16384];
        long intact = 0;
        try {
            for (int at = 8; at + 12 <= length && intact < height; ) {
                final int size = (int) be32(bytes, at);
                if (size > length - at - 12 || type(bytes, at + 4, "IEND")) break;
                if (type(bytes, at + 4, "IDAT")) {
                    final int skip = header ? Math.min(2, size) : 0;
                    header = false;
                    inflater.setInput(bytes, at + 8 + skip, size - skip);
                    boolean broken = false;
                    while (!inflater.needsInput() && !inflater.finished() && !broken) {
                        int produced;
                        try {
                            produced = inflater.inflate(chunk);
                        } catch (DataFormatException e) {
                            broken = true;
                            break;
                        }
                        if (produced == 0 && inflater.needsDictionary()) break;
                        raw.write(chunk, 0, produced);
                        final byte[] data = raw.toByteArray();
                        while (intact < height) {
                            final long row = intact * stride;
                            if (row >= data.length) break;
                            if ((data[(int) row] & 0xFF) > 4) {
                                broken = true;
                                break;
                            }
                            if (row + stride > data.length) break;
                            intact++;
                        }
                        if (produced == 0) break;
                    }
                    if (broken) break;
                } else if (raw.size() == 0 && !type(bytes, at + 4, "IHDR")) {
                    // PLTE, tRNS and the like: the rebuilt image needs them.
                    ancillary.write(bytes, at, size + 12);
                }
                at += size + 12;
            }
        } finally {
            inflater.end();
        }
        if (intact >= height) return null;

        Bitmap image = Bitmap.createBitmap((int) width, (int) height, Bitmap.Config.ARGB_8888);
        if (intact == 0) return image;
        // The intact rows as a whole PNG of their own, decoded by BitmapFactory onto the transparent image.
        byte[] rows = new byte[(int) (intact * stride)];
        System.arraycopy(raw.toByteArray(), 0, rows, 0, rows.length);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(SIGNATURE, 0, SIGNATURE.length);
        byte[] ihdr = new byte[13];
        System.arraycopy(bytes, 16, ihdr, 0, 13);
        putBe32(ihdr, 4, intact);
        writeChunk(png, "IHDR", ihdr, crc);
        byte[] extra = ancillary.toByteArray();
        png.write(extra, 0, extra.length);
        writeChunk(png, "IDAT", deflate(rows), crc);
        writeChunk(png, "IEND", new byte[0], crc);
        byte[] rebuilt = png.toByteArray();
        Bitmap top = BitmapFactory.decodeByteArray(rebuilt, 0, rebuilt.length);
        if (top == null) return image;
        new Canvas(image).drawBitmap(top, 0, 0, null);
        top.recycle();
        return image;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data, CRC32 crc) {
        byte[] head = new byte[8];
        putBe32(head, 0, data.length);
        for (int i = 0; i < 4; i++) head[4 + i] = (byte) type.charAt(i);
        out.write(head, 0, 8);
        out.write(data, 0, data.length);
        crc.reset();
        crc.update(head, 4, 4);
        crc.update(data, 0, data.length);
        byte[] tail = new byte[4];
        putBe32(tail, 0, crc.getValue());
        out.write(tail, 0, 4);
    }

    private static boolean startsWith(byte[] bytes, int at, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) if (bytes[at + i] != prefix[i]) return false;
        return true;
    }

    private static boolean type(byte[] bytes, int at, String type) {
        for (int i = 0; i < 4; i++) if (bytes[at + i] != type.charAt(i)) return false;
        return true;
    }

    private static long be32(byte[] bytes, int at) {
        return ((bytes[at] & 0xFFL) << 24) | ((bytes[at + 1] & 0xFFL) << 16) | ((bytes[at + 2] & 0xFFL) << 8) | (bytes[at + 3] & 0xFFL);
    }

    private static void putBe32(byte[] bytes, int at, long value) {
        bytes[at] = (byte) (value >>> 24);
        bytes[at + 1] = (byte) (value >>> 16);
        bytes[at + 2] = (byte) (value >>> 8);
        bytes[at + 3] = (byte) value;
    }
}

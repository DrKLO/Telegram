package org.telegram.messenger;

import androidx.media3.extractor.jpeg.MotionPhotoDescription;
import androidx.media3.extractor.jpeg.XmpMotionPhotoDescriptionParser;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

// HEIC motion photos keep the video in a top-level "mpvd" box and the XMP in a separate HEIF item that ExifInterface doesn't read,
// so read both directly from the box structure, without MediaMetadataRetriever (it's slow).
public class HeifMotionPhoto {

    public final long videoOffset;
    public final long timestampUs;

    private HeifMotionPhoto(long videoOffset, long timestampUs) {
        this.videoOffset = videoOffset;
        this.timestampUs = timestampUs;
    }

    public boolean isMotionPhoto() {
        return videoOffset > 0;
    }

    // Returns null if the file isn't ISOBMFF (e.g. JPEG), otherwise the parsed result, possibly not a motion photo.
    public static HeifMotionPhoto parse(File file) throws Exception {
        final long fileLength = file.length();
        long videoStart = -1;
        String xmp = null;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            ByteBuffer meta = null;
            long offset = 0;
            for (int boxes = 0; boxes < 64 && offset + 8 <= fileLength; ++boxes) {
                raf.seek(offset);
                long boxSize = raf.readInt() & 0xFFFFFFFFL;
                final int boxType = raf.readInt();
                int headerSize = 8;
                if (boxSize == 1) {
                    if (offset + 16 > fileLength) break;
                    boxSize = raf.readLong();
                    headerSize = 16;
                } else if (boxSize == 0) {
                    boxSize = fileLength - offset;
                }
                if (offset == 0 && boxType != 0x66747970) { // ftyp
                    return null;
                }
                if (boxSize < headerSize || boxSize > fileLength - offset) break;
                if (boxType == 0x6d657461 && meta == null && boxSize - headerSize <= 1024 * 1024) { // meta
                    final byte[] data = new byte[(int) (boxSize - headerSize)];
                    raf.readFully(data);
                    meta = ByteBuffer.wrap(data);
                } else if (boxType == 0x6d707664) { // mpvd
                    if (boxSize > headerSize) {
                        videoStart = offset + headerSize;
                    }
                    break;
                }
                offset += boxSize;
            }
            if (offset == 0) {
                return null;
            }
            if (meta != null) {
                try {
                    xmp = readXmp(raf, meta, fileLength);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        final MotionPhotoDescription motionPhoto = xmp == null ? null : XmpMotionPhotoDescriptionParser.parse(xmp);
        if (videoStart <= 0 && motionPhoto != null) {
            for (int i = 0; i < motionPhoto.items.size(); ++i) {
                final MotionPhotoDescription.ContainerItem item = motionPhoto.items.get(i);
                if ("MotionPhoto".equalsIgnoreCase(item.semantic) && item.length > 0 && item.length < fileLength) {
                    videoStart = fileLength - item.length;
                }
            }
        }
        if (videoStart > 0 && videoStart + 8 <= fileLength) {
            // the video must be an mp4 starting with ftyp, otherwise treat the file as a regular photo
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(videoStart + 4);
                if (raf.readInt() != 0x66747970) {
                    videoStart = -1;
                }
            }
        } else {
            videoStart = -1;
        }
        final long timestampUs = motionPhoto != null && motionPhoto.photoPresentationTimestampUs >= 0 ? motionPhoto.photoPresentationTimestampUs : 0;
        return new HeifMotionPhoto(videoStart, timestampUs);
    }

    private static String readXmp(RandomAccessFile raf, ByteBuffer meta, long fileLength) throws Exception {
        meta.position(4); // version, flags
        int xmpItemId = -1;
        ByteBuffer iloc = null;
        while (meta.remaining() >= 8) {
            final int start = meta.position();
            final long size = meta.getInt() & 0xFFFFFFFFL;
            final int type = meta.getInt();
            if (size < 8 || size > meta.limit() - start) break;
            final ByteBuffer box = (ByteBuffer) ((ByteBuffer) meta.duplicate().position(start + 8)).limit(start + (int) size);
            if (type == 0x69696e66) { // iinf
                final int version = box.get() & 0xFF;
                box.position(box.position() + 3);
                final long count = version == 0 ? box.getShort() & 0xFFFF : box.getInt() & 0xFFFFFFFFL;
                for (long i = 0; i < count && box.remaining() >= 8 && xmpItemId < 0; ++i) {
                    final int infeStart = box.position();
                    final long infeSize = box.getInt() & 0xFFFFFFFFL;
                    final int infeType = box.getInt();
                    if (infeSize < 8 || infeSize > box.limit() - infeStart) break;
                    if (infeType == 0x696e6665 && box.remaining() >= 12) { // infe
                        final int infeVersion = box.get() & 0xFF;
                        box.position(box.position() + 3);
                        if (infeVersion >= 2) {
                            final int itemId = infeVersion == 2 ? box.getShort() & 0xFFFF : box.getInt();
                            box.getShort(); // item_protection_index
                            final int itemType = box.getInt();
                            if (itemType == 0x6d696d65) { // mime
                                final String[] strings = readStrings(box, infeStart + (int) infeSize, 2); // item_name, content_type
                                if ("application/rdf+xml".equalsIgnoreCase(strings[1])) {
                                    xmpItemId = itemId;
                                }
                            }
                        }
                    }
                    box.position(infeStart + (int) infeSize);
                }
            } else if (type == 0x696c6f63) { // iloc
                iloc = box;
            }
            meta.position(start + (int) size);
        }
        if (xmpItemId < 0 || iloc == null) {
            return null;
        }

        final int version = iloc.get() & 0xFF;
        iloc.position(iloc.position() + 3);
        final int sizes = iloc.getShort() & 0xFFFF;
        final int offsetSize = (sizes >> 12) & 0xF, lengthSize = (sizes >> 8) & 0xF, baseOffsetSize = (sizes >> 4) & 0xF;
        final int indexSize = version == 1 || version == 2 ? sizes & 0xF : 0;
        if (!isValidFieldSize(offsetSize) || !isValidFieldSize(lengthSize) || !isValidFieldSize(baseOffsetSize) || !isValidFieldSize(indexSize)) {
            return null;
        }
        final int extentSize = indexSize + offsetSize + lengthSize;
        final long count = version < 2 ? iloc.getShort() & 0xFFFF : iloc.getInt() & 0xFFFFFFFFL;
        for (long i = 0; i < count; ++i) {
            final int itemId = version < 2 ? iloc.getShort() & 0xFFFF : iloc.getInt();
            final int constructionMethod = version == 1 || version == 2 ? iloc.getShort() & 0xF : 0;
            iloc.getShort(); // data_reference_index
            final long baseOffset = readSized(iloc, baseOffsetSize);
            final int extentCount = iloc.getShort() & 0xFFFF;
            if ((long) extentCount * extentSize > iloc.remaining()) {
                return null;
            }
            if (itemId != xmpItemId) {
                iloc.position(iloc.position() + extentCount * extentSize);
                continue;
            }
            if (constructionMethod != 0 || extentCount == 0 || extentCount > 64) {
                return null;
            }
            final long[] extentOffsets = new long[extentCount], extentLengths = new long[extentCount];
            long totalLength = 0;
            for (int e = 0; e < extentCount; ++e) {
                readSized(iloc, indexSize);
                extentOffsets[e] = baseOffset + readSized(iloc, offsetSize);
                extentLengths[e] = readSized(iloc, lengthSize);
                if (extentOffsets[e] < 0 || extentLengths[e] <= 0 || extentLengths[e] > fileLength - extentOffsets[e]) {
                    return null;
                }
                totalLength += extentLengths[e];
            }
            if (totalLength > 1024 * 1024) {
                return null;
            }
            final byte[] data = new byte[(int) totalLength];
            int position = 0;
            for (int e = 0; e < extentCount; ++e) {
                raf.seek(extentOffsets[e]);
                raf.readFully(data, position, (int) extentLengths[e]);
                position += (int) extentLengths[e];
            }
            return new String(data, "UTF-8");
        }
        return null;
    }

    private static boolean isValidFieldSize(int size) {
        return size == 0 || size == 4 || size == 8;
    }

    private static long readSized(ByteBuffer buffer, int size) {
        switch (size) {
            case 4: return buffer.getInt() & 0xFFFFFFFFL;
            case 8: return buffer.getLong();
            default: return 0;
        }
    }

    private static String[] readStrings(ByteBuffer buffer, int end, int count) {
        final String[] strings = new String[count];
        for (int i = 0; i < count && buffer.position() < end; ++i) {
            final int start = buffer.position();
            while (buffer.position() < end && buffer.get() != 0);
            final int length = buffer.position() - start - (buffer.get(buffer.position() - 1) == 0 ? 1 : 0);
            strings[i] = new String(buffer.array(), buffer.arrayOffset() + start, Math.max(0, length));
        }
        return strings;
    }
}

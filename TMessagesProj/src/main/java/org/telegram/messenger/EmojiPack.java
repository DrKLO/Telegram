package org.telegram.messenger;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.SparseArray;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public final class EmojiPack {

    private static final String ASSET_NAME = "emoji.pack";

    private static final int NO_MASK = 0xFFFF;

    private static final int EMOJI_ENTRY_SIZE =
            2 + // emojiIndex
            2 + // maskId
            4 + // pngOffset
            4;  // pngLength

    private static final int MASK_ENTRY_SIZE =
            2 + // maskId
            4 + // pngOffset
            4;  // pngLength

    private static EmojiPack instance;

    private final AssetFileDescriptor assetFileDescriptor;
    private final FileInputStream inputStream;
    private final FileChannel channel;
    private final MappedByteBuffer buffer;

    private final SparseArray<EmojiEntry> emojis = new SparseArray<>();
    private final SparseArray<ImageEntry> masks = new SparseArray<>();

    private byte[] decodeBuffer = new byte[1024];

    private EmojiPack() throws IOException {
        assetFileDescriptor =
                ApplicationLoader.applicationContext
                        .getAssets()
                        .openFd(ASSET_NAME);

        inputStream =
                new FileInputStream(
                        assetFileDescriptor.getFileDescriptor()
                );

        channel = inputStream.getChannel();

        buffer = channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.getStartOffset(),
                assetFileDescriptor.getLength()
        );

        buffer.order(ByteOrder.LITTLE_ENDIAN);

        readMetadata();
    }

    public static EmojiPack getInstance() {
        if (instance == null) {
            try {
                instance = new EmojiPack();
            } catch (IOException e) {
                throw new RuntimeException(
                        "Unable to open emoji pack",
                        e
                );
            }
        }

        return instance;
    }

    private void readMetadata() throws IOException {
        ByteBuffer bb = buffer.duplicate()
                .order(ByteOrder.LITTLE_ENDIAN);

        int emojiMetadataLength = bb.getInt();

        if (
                emojiMetadataLength < 0 ||
                        emojiMetadataLength % EMOJI_ENTRY_SIZE != 0
        ) {
            throw new IOException(
                    "Invalid emoji metadata length: " +
                            emojiMetadataLength
            );
        }

        int emojiCount =
                emojiMetadataLength / EMOJI_ENTRY_SIZE;

        for (int i = 0; i < emojiCount; i++) {
            int emojiIndex =
                    bb.getShort() & 0xFFFF;

            int maskId =
                    bb.getShort() & 0xFFFF;

            int offset = bb.getInt();
            int length = bb.getInt();

            validateRange(
                    offset,
                    length,
                    "emoji " + emojiIndex
            );

            emojis.put(
                    emojiIndex,
                    new EmojiEntry(
                            offset,
                            length,
                            maskId
                    )
            );
        }

        int maskMetadataLength = bb.getInt();

        if (
                maskMetadataLength < 0 ||
                        maskMetadataLength % MASK_ENTRY_SIZE != 0
        ) {
            throw new IOException(
                    "Invalid mask metadata length: " +
                            maskMetadataLength
            );
        }

        int maskCount =
                maskMetadataLength / MASK_ENTRY_SIZE;

        for (int i = 0; i < maskCount; i++) {
            int maskId =
                    bb.getShort() & 0xFFFF;

            int offset = bb.getInt();
            int length = bb.getInt();

            validateRange(
                    offset,
                    length,
                    "mask " + maskId
            );

            masks.put(
                    maskId,
                    new ImageEntry(
                            offset,
                            length
                    )
            );
        }
    }

    private void validateRange(
            int offset,
            int length,
            String name
    ) throws IOException {
        if (offset < 0 || length < 0) {
            throw new IOException(
                    "Invalid range for " + name +
                            ": offset=" + offset +
                            ", length=" + length
            );
        }

        long end =
                (long) offset + length;

        if (end > buffer.capacity()) {
            throw new IOException(
                    "Range outside emoji pack for " + name +
                            ": offset=" + offset +
                            ", length=" + length +
                            ", packSize=" + buffer.capacity()
            );
        }
    }

    public Bitmap getEmoji(
            int x,
            int y
    ) {
        if (x < 0 || y < 0 || y >= 4096) {
            return null;
        }

        long index =
                (long) x * 4096L + y;

        if (index > 0xFFFFL) {
            return null;
        }

        EmojiEntry entry =
                emojis.get((int) index);

        if (entry == null) {
            return null;
        }

        return decode(entry);
    }

    public Bitmap getMask(
            int maskId
    ) {
        ImageEntry entry =
                masks.get(maskId);

        if (entry == null) {
            return null;
        }

        return decode(entry);
    }

    public int getMaskId(
            int x,
            int y
    ) {
        if (x < 0 || y < 0 || y >= 4096) {
            return NO_MASK;
        }

        long index =
                (long) x * 4096L + y;

        if (index > 0xFFFFL) {
            return NO_MASK;
        }

        EmojiEntry entry =
                emojis.get((int) index);

        return entry != null
                ? entry.maskId
                : NO_MASK;
    }

    private Bitmap decode(
            ImageEntry entry
    ) {
        if (decodeBuffer.length < entry.length) {
            int newSize = decodeBuffer.length;

            while (newSize < entry.length) {
                newSize <<= 1;
            }

            decodeBuffer = new byte[newSize];
        }

        buffer.position(entry.offset);
        buffer.get(
                decodeBuffer,
                0,
                entry.length
        );

        return BitmapFactory.decodeByteArray(
                decodeBuffer,
                0,
                entry.length
        );
    }

    private static class ImageEntry {

        final int offset;
        final int length;

        ImageEntry(
                int offset,
                int length
        ) {
            this.offset = offset;
            this.length = length;
        }
    }

    private static final class EmojiEntry
            extends ImageEntry {

        final int maskId;

        EmojiEntry(
                int offset,
                int length,
                int maskId
        ) {
            super(
                    offset,
                    length
            );

            this.maskId = maskId;
        }
    }
}
package org.telegram.ui.Wallet;

import org.telegram.messenger.Utilities;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * The normalised hash of a signed external message (TEP-467): the hash of the message with no source, no import fee, no
 * StateInit and the body in a reference. toncenter and tonapi have it as `hash_norm`, and a widget's bot takes it as
 * `msg_hash` (widget-sandbox docs/sdk-v1.md §5.13). `updateSentWalletTransaction.msg_hash` is the message's plain hash, and
 * the engine does not give this one out with a prepared transfer.
 */
final class WalletMessageHash {

    private static final int MAX_REFS = 4;
    private static final int NORMALIZED_BITS = 2 + 2 + 267 + 4 + 1 + 1;

    private WalletMessageHash() {
    }

    /** 64 lowercase hex digits; null if the bag of cells isn't an external message to a standard address. */
    static String normalized(byte[] boc) {
        try {
            ByteBuffer in = ByteBuffer.wrap(boc);
            if (in.getInt() != 0xb5ee9c72) return null;
            final int flags = in.get() & 0xff;
            final int refSize = flags & 7;
            final int offsetSize = in.get() & 0xff;
            if (refSize < 1 || refSize > 4 || offsetSize < 1 || offsetSize > 8) return null;
            final int count = (int) uint(in, refSize);
            final long roots = uint(in, refSize);
            uint(in, refSize); // absent cells
            uint(in, offsetSize); // the cells' size
            if (roots != 1 || count < 1 || count > boc.length) return null;
            final int root = (int) uint(in, refSize);
            if ((flags & 0x80) != 0) in.position(in.position() + count * offsetSize); // the index

            final byte[][] data = new byte[count][];
            final int[][] refs = new int[count][];
            final int[] descriptor = new int[count];
            for (int i = 0; i < count; i++) {
                final int d1 = in.get() & 0xff;
                final int d2 = in.get() & 0xff;
                // Only ordinary cells: an exotic one or a level above 0 hashes differently.
                if (d1 > MAX_REFS) return null;
                descriptor[i] = d2;
                data[i] = new byte[(d2 + 1) / 2];
                in.get(data[i]);
                refs[i] = new int[d1];
                for (int r = 0; r < d1; r++) {
                    refs[i][r] = (int) uint(in, refSize);
                    if (refs[i][r] <= i || refs[i][r] >= count) return null;
                }
            }

            // A cell's references come after it, so from the last cell its children are known.
            final int[] depth = new int[count];
            final byte[][] hash = new byte[count][];
            for (int i = count - 1; i >= 0; i--) {
                final MessageDigest sha = MessageDigest.getInstance("SHA-256");
                sha.update((byte) refs[i].length);
                sha.update((byte) descriptor[i]);
                sha.update(data[i]);
                for (int ref : refs[i]) {
                    depth[i] = Math.max(depth[i], depth[ref] + 1);
                    sha.update((byte) (depth[ref] >> 8));
                    sha.update((byte) depth[ref]);
                }
                for (int ref : refs[i]) sha.update(hash[ref]);
                hash[i] = sha.digest();
            }

            final int body = bodyOf(data[root], refs[root]);
            if (body < 0) return null;

            // ext_in_msg_info$10, src addr_none$00, the destination, import_fee 0, no init, the body in a reference.
            final byte[] normalized = new byte[(NORMALIZED_BITS + 8) / 8];
            setBit(normalized, 0);
            for (int i = 0; i < 267; i++) {
                if (bit(data[root], 4 + i)) setBit(normalized, 4 + i);
            }
            setBit(normalized, NORMALIZED_BITS - 1);
            setBit(normalized, NORMALIZED_BITS); // the completion tag
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((byte) 1);
            sha.update((byte) (NORMALIZED_BITS / 8 + (NORMALIZED_BITS + 7) / 8));
            sha.update(normalized);
            sha.update((byte) (depth[body] >> 8));
            sha.update((byte) depth[body]);
            sha.update(hash[body]);
            return Utilities.bytesToHex(sha.digest()).toLowerCase(Locale.ROOT);
        } catch (RuntimeException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** The reference that holds the message's body, or -1 if the root isn't `ext_in_msg_info` with its body in a reference. */
    private static int bodyOf(byte[] root, int[] refs) {
        // 10 (ext_in_msg_info), 00 (no source), 100 (addr_std, no anycast).
        if (!bit(root, 0) || bit(root, 1) || bit(root, 2) || bit(root, 3) || !bit(root, 4) || bit(root, 5) || bit(root, 6)) return -1;
        int at = 4 + 267;
        int feeBytes = 0;
        for (int i = 0; i < 4; i++) feeBytes = feeBytes << 1 | (bit(root, at++) ? 1 : 0);
        at += feeBytes * 8;
        if (bit(root, at++) && !bit(root, at++)) {
            // A StateInit in place: split_depth and special, each a flag and its value, then code, data and library.
            if (bit(root, at++)) at += 5;
            if (bit(root, at++)) at += 2;
            at += 3;
        }
        return bit(root, at) && refs.length > 0 ? refs[refs.length - 1] : -1;
    }

    private static boolean bit(byte[] data, int at) {
        return (data[at / 8] >> (7 - at % 8) & 1) != 0;
    }

    private static void setBit(byte[] data, int at) {
        data[at / 8] |= (byte) (1 << (7 - at % 8));
    }

    private static long uint(ByteBuffer in, int size) {
        long value = 0;
        for (int i = 0; i < size; i++) value = value << 8 | in.get() & 0xff;
        return value;
    }
}

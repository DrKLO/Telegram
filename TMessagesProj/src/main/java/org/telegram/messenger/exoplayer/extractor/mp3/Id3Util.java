/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.exoplayer.extractor.mp3;

import android.util.Pair;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.GaplessInfo;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Utility for parsing ID3 version 2 metadata in MP3 files.
 */
/* package */ final class Id3Util {

  /**
   * The maximum valid length for metadata in bytes.
   */
  private static final int MAXIMUM_METADATA_SIZE = 3 * 1024 * 1024;

  private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
  private static final Charset[] CHARSET_BY_ENCODING = new Charset[] {Charset.forName("ISO-8859-1"),
      Charset.forName("UTF-16LE"), Charset.forName("UTF-16BE"), Charset.forName("UTF-8")};

  /**
   * Peeks data from the input and parses ID3 metadata.
   *
   * @param input The {@link ExtractorInput} from which data should be peeked.
   * @return The gapless playback information, if present and non-zero. {@code null} otherwise.
   * @throws IOException If an error occurred peeking from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  public static GaplessInfo parseId3(ExtractorInput input)
      throws IOException, InterruptedException {
    ParsableByteArray scratch = new ParsableByteArray(10);
    int peekedId3Bytes = 0;
    GaplessInfo metadata = null;
    while (true) {
      input.peekFully(scratch.data, 0, 10);
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() != ID3_TAG) {
        break;
      }

      int majorVersion = scratch.readUnsignedByte();
      int minorVersion = scratch.readUnsignedByte();
      int flags = scratch.readUnsignedByte();
      int length = scratch.readSynchSafeInt();
      if (metadata == null && canParseMetadata(majorVersion, minorVersion, flags, length)) {
        byte[] frame = new byte[length];
        input.peekFully(frame, 0, length);
        metadata = parseGaplessInfo(new ParsableByteArray(frame), majorVersion, flags);
      } else {
        input.advancePeekPosition(length);
      }

      peekedId3Bytes += 10 + length;
    }
    input.resetPeekPosition();
    input.advancePeekPosition(peekedId3Bytes);
    return metadata;
  }

  private static boolean canParseMetadata(int majorVersion, int minorVersion, int flags,
      int length) {
    return minorVersion != 0xFF && majorVersion >= 2 && majorVersion <= 4
        && length <= MAXIMUM_METADATA_SIZE
        && !(majorVersion == 2 && ((flags & 0x3F) != 0 || (flags & 0x40) != 0))
        && !(majorVersion == 3 && (flags & 0x1F) != 0)
        && !(majorVersion == 4 && (flags & 0x0F) != 0);
  }

  private static GaplessInfo parseGaplessInfo(ParsableByteArray frame, int version, int flags) {
    unescape(frame, version, flags);

    // Skip any extended header.
    frame.setPosition(0);
    if (version == 3 && (flags & 0x40) != 0) {
      if (frame.bytesLeft() < 4) {
        return null;
      }
      int extendedHeaderSize = frame.readUnsignedIntToInt();
      if (extendedHeaderSize > frame.bytesLeft()) {
        return null;
      }
      int paddingSize = 0;
      if (extendedHeaderSize >= 6) {
        frame.skipBytes(2); // extended flags
        paddingSize = frame.readUnsignedIntToInt();
        frame.setPosition(4);
        frame.setLimit(frame.limit() - paddingSize);
        if (frame.bytesLeft() < extendedHeaderSize) {
          return null;
        }
      }
      frame.skipBytes(extendedHeaderSize);
    } else if (version == 4 && (flags & 0x40) != 0) {
      if (frame.bytesLeft() < 4) {
        return null;
      }
      int extendedHeaderSize = frame.readSynchSafeInt();
      if (extendedHeaderSize < 6 || extendedHeaderSize > frame.bytesLeft() + 4) {
        return null;
      }
      frame.setPosition(extendedHeaderSize);
    }

    // Extract gapless playback metadata stored in comments.
    Pair<String, String> comment;
    while ((comment = findNextComment(version, frame)) != null) {
      if (comment.first.length() > 3) {
        GaplessInfo gaplessInfo =
            GaplessInfo.createFromComment(comment.first.substring(3), comment.second);
        if (gaplessInfo != null) {
          return gaplessInfo;
        }
      }
    }
    return null;
  }

  private static Pair<String, String> findNextComment(int majorVersion, ParsableByteArray data) {
    int frameSize;
    while (true) {
      if (majorVersion == 2) {
        if (data.bytesLeft() < 6) {
          return null;
        }
        String id = data.readString(3, Charset.forName("US-ASCII"));
        if (id.equals("\0\0\0")) {
          return null;
        }
        frameSize = data.readUnsignedInt24();
        if (frameSize == 0 || frameSize > data.bytesLeft()) {
          return null;
        }
        if (id.equals("COM")) {
          break;
        }
      } else /* major == 3 || major == 4 */ {
        if (data.bytesLeft() < 10) {
          return null;
        }
        String id = data.readString(4, Charset.forName("US-ASCII"));
        if (id.equals("\0\0\0\0")) {
          return null;
        }
        frameSize = majorVersion == 4 ? data.readSynchSafeInt() : data.readUnsignedIntToInt();
        if (frameSize == 0 || frameSize > data.bytesLeft() - 2) {
          return null;
        }
        int flags = data.readUnsignedShort();
        boolean compressedOrEncrypted = (majorVersion == 4 && (flags & 0x0C) != 0)
            || (majorVersion == 3 && (flags & 0xC0) != 0);
        if (!compressedOrEncrypted && id.equals("COMM")) {
          break;
        }
      }
      data.skipBytes(frameSize);
    }

    // The comment tag is at the reading position in data.
    int encoding = data.readUnsignedByte();
    if (encoding < 0 || encoding >= CHARSET_BY_ENCODING.length) {
      return null;
    }
    Charset charset = CHARSET_BY_ENCODING[encoding];
    String[] commentFields = data.readString(frameSize - 1, charset).split("\0");
    return commentFields.length == 2 ? Pair.create(commentFields[0], commentFields[1]) : null;
  }

  private static boolean unescape(ParsableByteArray frame, int version, int flags) {
    if (version != 4) {
      if ((flags & 0x80) != 0) {
        // Remove unsynchronization on ID3 version < 2.4.0.
        byte[] bytes = frame.data;
        int newLength = bytes.length;
        for (int i = 0; i + 1 < newLength; i++) {
          if ((bytes[i] & 0xFF) == 0xFF && bytes[i + 1] == 0x00) {
            System.arraycopy(bytes, i + 2, bytes, i + 1, newLength - i - 2);
            newLength--;
          }
        }
        frame.setLimit(newLength);
      }
    } else {
      // Remove unsynchronization on ID3 version 2.4.0.
      if (canUnescapeVersion4(frame, false)) {
        unescapeVersion4(frame, false);
      } else if (canUnescapeVersion4(frame, true)) {
        unescapeVersion4(frame, true);
      } else {
        return false;
      }
    }
    return true;
  }

  private static boolean canUnescapeVersion4(ParsableByteArray frame,
      boolean unsignedIntDataSizeHack) {
    frame.setPosition(0);
    while (frame.bytesLeft() >= 10) {
      if (frame.readInt() == 0) {
        return true;
      }
      long dataSize = frame.readUnsignedInt();
      if (!unsignedIntDataSizeHack) {
        // Parse the data size as a syncsafe integer.
        if ((dataSize & 0x808080L) != 0) {
          return false;
        }
        dataSize = (dataSize & 0x7F) | (((dataSize >> 8) & 0x7F) << 7)
            | (((dataSize >> 16) & 0x7F) << 14) | (((dataSize >> 24) & 0x7F) << 21);
      }
      if (dataSize > frame.bytesLeft() - 2) {
        return false;
      }
      int flags = frame.readUnsignedShort();
      if ((flags & 1) != 0) {
        if (frame.bytesLeft() < 4) {
          return false;
        }
      }
      frame.skipBytes((int) dataSize);
    }
    return true;
  }

  private static void unescapeVersion4(ParsableByteArray frame, boolean unsignedIntDataSizeHack) {
    frame.setPosition(0);
    byte[] bytes = frame.data;
    while (frame.bytesLeft() >= 10) {
      if (frame.readInt() == 0) {
        return;
      }
      int dataSize =
          unsignedIntDataSizeHack ? frame.readUnsignedIntToInt() : frame.readSynchSafeInt();
      int flags = frame.readUnsignedShort();
      int previousFlags = flags;
      if ((flags & 1) != 0) {
        // Strip data length indicator.
        int offset = frame.getPosition();
        System.arraycopy(bytes, offset + 4, bytes, offset, frame.bytesLeft() - 4);
        dataSize -= 4;
        flags &= ~1;
        frame.setLimit(frame.limit() - 4);
      }
      if ((flags & 2) != 0) {
        // Unescape 0xFF00 to 0xFF in the next dataSize bytes.
        int readOffset = frame.getPosition() + 1;
        int writeOffset = readOffset;
        for (int i = 0; i + 1 < dataSize; i++) {
          if ((bytes[readOffset - 1] & 0xFF) == 0xFF && bytes[readOffset] == 0) {
            readOffset++;
            dataSize--;
          }
          bytes[writeOffset++] = bytes[readOffset++];
        }
        frame.setLimit(frame.limit() - (readOffset - writeOffset));
        System.arraycopy(bytes, readOffset, bytes, writeOffset, frame.bytesLeft() - readOffset);
        flags &= ~2;
      }
      if (flags != previousFlags || unsignedIntDataSizeHack) {
        int dataSizeOffset = frame.getPosition() - 6;
        writeSyncSafeInteger(bytes, dataSizeOffset, dataSize);
        bytes[dataSizeOffset + 4] = (byte) (flags >> 8);
        bytes[dataSizeOffset + 5] = (byte) (flags & 0xFF);
      }
      frame.skipBytes(dataSize);
    }
  }

  private static void writeSyncSafeInteger(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte) ((value >> 21) & 0x7F);
    bytes[offset + 1] = (byte) ((value >> 14) & 0x7F);
    bytes[offset + 2] = (byte) ((value >> 7) & 0x7F);
    bytes[offset + 3] = (byte) (value & 0x7F);
  }

  private Id3Util() {}

}

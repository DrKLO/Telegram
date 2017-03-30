/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.metadata.id3;

import android.util.Log;
import org.telegram.messenger.exoplayer2.metadata.Metadata;
import org.telegram.messenger.exoplayer2.metadata.MetadataDecoder;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Decodes ID3 tags.
 */
public final class Id3Decoder implements MetadataDecoder {

  private static final String TAG = "Id3Decoder";

  /**
   * The first three bytes of a well formed ID3 tag header.
   */
  public static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
  /**
   * Length of an ID3 tag header.
   */
  public static final int ID3_HEADER_LENGTH = 10;

  private static final int ID3_TEXT_ENCODING_ISO_8859_1 = 0;
  private static final int ID3_TEXT_ENCODING_UTF_16 = 1;
  private static final int ID3_TEXT_ENCODING_UTF_16BE = 2;
  private static final int ID3_TEXT_ENCODING_UTF_8 = 3;

  @Override
  public boolean canDecode(String mimeType) {
    return mimeType.equals(MimeTypes.APPLICATION_ID3);
  }

  @Override
  public Metadata decode(byte[] data, int size) {
    List<Id3Frame> id3Frames = new ArrayList<>();
    ParsableByteArray id3Data = new ParsableByteArray(data, size);

    Id3Header id3Header = decodeHeader(id3Data);
    if (id3Header == null) {
      return null;
    }

    int startPosition = id3Data.getPosition();
    int framesSize = id3Header.framesSize;
    if (id3Header.isUnsynchronized) {
      framesSize = removeUnsynchronization(id3Data, id3Header.framesSize);
    }
    id3Data.setLimit(startPosition + framesSize);

    boolean unsignedIntFrameSizeHack = false;
    if (id3Header.majorVersion == 4) {
      if (!validateV4Frames(id3Data, false)) {
        if (validateV4Frames(id3Data, true)) {
          unsignedIntFrameSizeHack = true;
        } else {
          Log.w(TAG, "Failed to validate V4 ID3 tag");
          return null;
        }
      }
    }

    int frameHeaderSize = id3Header.majorVersion == 2 ? 6 : 10;
    while (id3Data.bytesLeft() >= frameHeaderSize) {
      Id3Frame frame = decodeFrame(id3Header.majorVersion, id3Data, unsignedIntFrameSizeHack);
      if (frame != null) {
        id3Frames.add(frame);
      }
    }

    return new Metadata(id3Frames);
  }

  /**
   * @param data A {@link ParsableByteArray} from which the header should be read.
   * @return The parsed header, or null if the ID3 tag is unsupported.
   */
  private static Id3Header decodeHeader(ParsableByteArray data) {
    if (data.bytesLeft() < ID3_HEADER_LENGTH) {
      Log.w(TAG, "Data too short to be an ID3 tag");
      return null;
    }

    int id = data.readUnsignedInt24();
    if (id != ID3_TAG) {
      Log.w(TAG, "Unexpected first three bytes of ID3 tag header: " + id);
      return null;
    }

    int majorVersion = data.readUnsignedByte();
    data.skipBytes(1); // Skip minor version.
    int flags = data.readUnsignedByte();
    int framesSize = data.readSynchSafeInt();

    if (majorVersion == 2) {
      boolean isCompressed = (flags & 0x40) != 0;
      if (isCompressed) {
        Log.w(TAG, "Skipped ID3 tag with majorVersion=2 and undefined compression scheme");
        return null;
      }
    } else if (majorVersion == 3) {
      boolean hasExtendedHeader = (flags & 0x40) != 0;
      if (hasExtendedHeader) {
        int extendedHeaderSize = data.readInt(); // Size excluding size field.
        data.skipBytes(extendedHeaderSize);
        framesSize -= (extendedHeaderSize + 4);
      }
    } else if (majorVersion == 4) {
      boolean hasExtendedHeader = (flags & 0x40) != 0;
      if (hasExtendedHeader) {
        int extendedHeaderSize = data.readSynchSafeInt(); // Size including size field.
        data.skipBytes(extendedHeaderSize - 4);
        framesSize -= extendedHeaderSize;
      }
      boolean hasFooter = (flags & 0x10) != 0;
      if (hasFooter) {
        framesSize -= 10;
      }
    } else {
      Log.w(TAG, "Skipped ID3 tag with unsupported majorVersion=" + majorVersion);
      return null;
    }

    // isUnsynchronized is advisory only in version 4. Frame level flags are used instead.
    boolean isUnsynchronized = majorVersion < 4 && (flags & 0x80) != 0;
    return new Id3Header(majorVersion, isUnsynchronized, framesSize);
  }

  private static boolean validateV4Frames(ParsableByteArray id3Data,
      boolean unsignedIntFrameSizeHack) {
    int startPosition = id3Data.getPosition();
    try {
      while (id3Data.bytesLeft() >= 10) {
        int id = id3Data.readInt();
        int frameSize = id3Data.readUnsignedIntToInt();
        int flags = id3Data.readUnsignedShort();
        if (id == 0 && frameSize == 0 && flags == 0) {
          return true;
        } else {
          if (!unsignedIntFrameSizeHack) {
            // Parse the data size as a synchsafe integer, as per the spec.
            if ((frameSize & 0x808080L) != 0) {
              return false;
            }
            frameSize = (frameSize & 0xFF) | (((frameSize >> 8) & 0xFF) << 7)
                | (((frameSize >> 16) & 0xFF) << 14) | (((frameSize >> 24) & 0xFF) << 21);
          }
          int minimumFrameSize = 0;
          if ((flags & 0x0040) != 0 /* hasGroupIdentifier */) {
            minimumFrameSize++;
          }
          if ((flags & 0x0001) != 0 /* hasDataLength */) {
            minimumFrameSize += 4;
          }
          if (frameSize < minimumFrameSize) {
            return false;
          }
          if (id3Data.bytesLeft() < frameSize) {
            return false;
          }
          id3Data.skipBytes(frameSize); // flags
        }
      }
      return true;
    } finally {
      id3Data.setPosition(startPosition);
    }
  }

  private static Id3Frame decodeFrame(int majorVersion, ParsableByteArray id3Data,
      boolean unsignedIntFrameSizeHack)  {
    int frameId0 = id3Data.readUnsignedByte();
    int frameId1 = id3Data.readUnsignedByte();
    int frameId2 = id3Data.readUnsignedByte();
    int frameId3 = majorVersion >= 3 ? id3Data.readUnsignedByte() : 0;

    int frameSize;
    if (majorVersion == 4) {
      frameSize = id3Data.readUnsignedIntToInt();
      if (!unsignedIntFrameSizeHack) {
        frameSize = (frameSize & 0xFF) | (((frameSize >> 8) & 0xFF) << 7)
            | (((frameSize >> 16) & 0xFF) << 14) | (((frameSize >> 24) & 0xFF) << 21);
      }
    } else if (majorVersion == 3) {
      frameSize = id3Data.readUnsignedIntToInt();
    } else /* id3Header.majorVersion == 2 */ {
      frameSize = id3Data.readUnsignedInt24();
    }

    int flags = majorVersion >= 3 ? id3Data.readUnsignedShort() : 0;
    if (frameId0 == 0 && frameId1 == 0 && frameId2 == 0 && frameId3 == 0 && frameSize == 0
        && flags == 0) {
      // We must be reading zero padding at the end of the tag.
      id3Data.setPosition(id3Data.limit());
      return null;
    }

    int nextFramePosition = id3Data.getPosition() + frameSize;
    if (nextFramePosition > id3Data.limit()) {
      Log.w(TAG, "Frame size exceeds remaining tag data");
      id3Data.setPosition(id3Data.limit());
      return null;
    }

    // Frame flags.
    boolean isCompressed = false;
    boolean isEncrypted = false;
    boolean isUnsynchronized = false;
    boolean hasDataLength = false;
    boolean hasGroupIdentifier = false;
    if (majorVersion == 3) {
      isCompressed = (flags & 0x0080) != 0;
      isEncrypted = (flags & 0x0040) != 0;
      hasGroupIdentifier = (flags & 0x0020) != 0;
      hasDataLength = isCompressed;
    } else if (majorVersion == 4) {
      hasGroupIdentifier = (flags & 0x0040) != 0;
      isCompressed = (flags & 0x0008) != 0;
      isEncrypted = (flags & 0x0004) != 0;
      isUnsynchronized = (flags & 0x0002) != 0;
      hasDataLength = (flags & 0x0001) != 0;
    }

    if (isCompressed || isEncrypted) {
      Log.w(TAG, "Skipping unsupported compressed or encrypted frame");
      id3Data.setPosition(nextFramePosition);
      return null;
    }

    if (hasGroupIdentifier) {
      frameSize--;
      id3Data.skipBytes(1);
    }
    if (hasDataLength) {
      frameSize -= 4;
      id3Data.skipBytes(4);
    }
    if (isUnsynchronized) {
      frameSize = removeUnsynchronization(id3Data, frameSize);
    }

    try {
      Id3Frame frame;
      if (frameId0 == 'T' && frameId1 == 'X' && frameId2 == 'X'
          && (majorVersion == 2 || frameId3 == 'X')) {
        frame = decodeTxxxFrame(id3Data, frameSize);
      } else if (frameId0 == 'P' && frameId1 == 'R' && frameId2 == 'I' && frameId3 == 'V') {
        frame = decodePrivFrame(id3Data, frameSize);
      } else if (frameId0 == 'G' && frameId1 == 'E' && frameId2 == 'O'
          && (frameId3 == 'B' || majorVersion == 2)) {
        frame = decodeGeobFrame(id3Data, frameSize);
      } else if (majorVersion == 2 ? (frameId0 == 'P' && frameId1 == 'I' && frameId2 == 'C')
          : (frameId0 == 'A' && frameId1 == 'P' && frameId2 == 'I' && frameId3 == 'C')) {
        frame = decodeApicFrame(id3Data, frameSize, majorVersion);
      } else if (frameId0 == 'T') {
        String id = majorVersion == 2
            ? String.format(Locale.US, "%c%c%c", frameId0, frameId1, frameId2)
            : String.format(Locale.US, "%c%c%c%c", frameId0, frameId1, frameId2, frameId3);
        frame = decodeTextInformationFrame(id3Data, frameSize, id);
      } else if (frameId0 == 'C' && frameId1 == 'O' && frameId2 == 'M'
          && (frameId3 == 'M' || majorVersion == 2)) {
        frame = decodeCommentFrame(id3Data, frameSize);
      } else {
        String id = majorVersion == 2
            ? String.format(Locale.US, "%c%c%c", frameId0, frameId1, frameId2)
            : String.format(Locale.US, "%c%c%c%c", frameId0, frameId1, frameId2, frameId3);
        frame = decodeBinaryFrame(id3Data, frameSize, id);
      }
      return frame;
    } catch (UnsupportedEncodingException e) {
      Log.w(TAG, "Unsupported character encoding");
      return null;
    } finally {
      id3Data.setPosition(nextFramePosition);
    }
  }

  private static TxxxFrame decodeTxxxFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int descriptionEndIndex = indexOfEos(data, 0, encoding);
    String description = new String(data, 0, descriptionEndIndex, charset);

    int valueStartIndex = descriptionEndIndex + delimiterLength(encoding);
    int valueEndIndex = indexOfEos(data, valueStartIndex, encoding);
    String value = new String(data, valueStartIndex, valueEndIndex - valueStartIndex, charset);

    return new TxxxFrame(description, value);
  }

  private static PrivFrame decodePrivFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    byte[] data = new byte[frameSize];
    id3Data.readBytes(data, 0, frameSize);

    int ownerEndIndex = indexOfZeroByte(data, 0);
    String owner = new String(data, 0, ownerEndIndex, "ISO-8859-1");

    int privateDataStartIndex = ownerEndIndex + 1;
    byte[] privateData = Arrays.copyOfRange(data, privateDataStartIndex, data.length);

    return new PrivFrame(owner, privateData);
  }

  private static GeobFrame decodeGeobFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int mimeTypeEndIndex = indexOfZeroByte(data, 0);
    String mimeType = new String(data, 0, mimeTypeEndIndex, "ISO-8859-1");

    int filenameStartIndex = mimeTypeEndIndex + 1;
    int filenameEndIndex = indexOfEos(data, filenameStartIndex, encoding);
    String filename = new String(data, filenameStartIndex, filenameEndIndex - filenameStartIndex,
        charset);

    int descriptionStartIndex = filenameEndIndex + delimiterLength(encoding);
    int descriptionEndIndex = indexOfEos(data, descriptionStartIndex, encoding);
    String description = new String(data, descriptionStartIndex,
        descriptionEndIndex - descriptionStartIndex, charset);

    int objectDataStartIndex = descriptionEndIndex + delimiterLength(encoding);
    byte[] objectData = Arrays.copyOfRange(data, objectDataStartIndex, data.length);

    return new GeobFrame(mimeType, filename, description, objectData);
  }

  private static ApicFrame decodeApicFrame(ParsableByteArray id3Data, int frameSize,
      int majorVersion) throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    String mimeType;
    int mimeTypeEndIndex;
    if (majorVersion == 2) {
      mimeTypeEndIndex = 2;
      mimeType = "image/" + new String(data, 0, 3, "ISO-8859-1").toLowerCase();
      if (mimeType.equals("image/jpg")) {
        mimeType = "image/jpeg";
      }
    } else {
      mimeTypeEndIndex = indexOfZeroByte(data, 0);
      mimeType = new String(data, 0, mimeTypeEndIndex, "ISO-8859-1").toLowerCase();
      if (mimeType.indexOf('/') == -1) {
        mimeType = "image/" + mimeType;
      }
    }

    int pictureType = data[mimeTypeEndIndex + 1] & 0xFF;

    int descriptionStartIndex = mimeTypeEndIndex + 2;
    int descriptionEndIndex = indexOfEos(data, descriptionStartIndex, encoding);
    String description = new String(data, descriptionStartIndex,
        descriptionEndIndex - descriptionStartIndex, charset);

    int pictureDataStartIndex = descriptionEndIndex + delimiterLength(encoding);
    byte[] pictureData = Arrays.copyOfRange(data, pictureDataStartIndex, data.length);

    return new ApicFrame(mimeType, description, pictureType, pictureData);
  }

  private static CommentFrame decodeCommentFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[3];
    id3Data.readBytes(data, 0, 3);
    String language = new String(data, 0, 3);

    data = new byte[frameSize - 4];
    id3Data.readBytes(data, 0, frameSize - 4);

    int descriptionEndIndex = indexOfEos(data, 0, encoding);
    String description = new String(data, 0, descriptionEndIndex, charset);

    int textStartIndex = descriptionEndIndex + delimiterLength(encoding);
    int textEndIndex = indexOfEos(data, textStartIndex, encoding);
    String text = new String(data, textStartIndex, textEndIndex - textStartIndex, charset);

    return new CommentFrame(language, description, text);
  }

  private static TextInformationFrame decodeTextInformationFrame(ParsableByteArray id3Data,
      int frameSize, String id) throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int descriptionEndIndex = indexOfEos(data, 0, encoding);
    String description = new String(data, 0, descriptionEndIndex, charset);

    return new TextInformationFrame(id, description);
  }

  private static BinaryFrame decodeBinaryFrame(ParsableByteArray id3Data, int frameSize,
      String id) {
    byte[] frame = new byte[frameSize];
    id3Data.readBytes(frame, 0, frameSize);

    return new BinaryFrame(id, frame);
  }

  /**
   * Performs in-place removal of unsynchronization for {@code length} bytes starting from
   * {@link ParsableByteArray#getPosition()}
   *
   * @param data Contains the data to be processed.
   * @param length The length of the data to be processed.
   * @return The length of the data after processing.
   */
  private static int removeUnsynchronization(ParsableByteArray data, int length) {
    byte[] bytes = data.data;
    for (int i = data.getPosition(); i + 1 < length; i++) {
      if ((bytes[i] & 0xFF) == 0xFF && bytes[i + 1] == 0x00) {
        System.arraycopy(bytes, i + 2, bytes, i + 1, length - i - 2);
        length--;
      }
    }
    return length;
  }

  /**
   * Maps encoding byte from ID3v2 frame to a Charset.
   * @param encodingByte The value of encoding byte from ID3v2 frame.
   * @return Charset name.
   */
  private static String getCharsetName(int encodingByte) {
    switch (encodingByte) {
      case ID3_TEXT_ENCODING_ISO_8859_1:
        return "ISO-8859-1";
      case ID3_TEXT_ENCODING_UTF_16:
        return "UTF-16";
      case ID3_TEXT_ENCODING_UTF_16BE:
        return "UTF-16BE";
      case ID3_TEXT_ENCODING_UTF_8:
        return "UTF-8";
      default:
        return "ISO-8859-1";
    }
  }

  private static int indexOfEos(byte[] data, int fromIndex, int encoding) {
    int terminationPos = indexOfZeroByte(data, fromIndex);

    // For single byte encoding charsets, we're done.
    if (encoding == ID3_TEXT_ENCODING_ISO_8859_1 || encoding == ID3_TEXT_ENCODING_UTF_8) {
      return terminationPos;
    }

    // Otherwise ensure an even index and look for a second zero byte.
    while (terminationPos < data.length - 1) {
      if (terminationPos % 2 == 0 && data[terminationPos + 1] == (byte) 0) {
        return terminationPos;
      }
      terminationPos = indexOfZeroByte(data, terminationPos + 1);
    }

    return data.length;
  }

  private static int indexOfZeroByte(byte[] data, int fromIndex) {
    for (int i = fromIndex; i < data.length; i++) {
      if (data[i] == (byte) 0) {
        return i;
      }
    }
    return data.length;
  }

  private static int delimiterLength(int encodingByte) {
    return (encodingByte == ID3_TEXT_ENCODING_ISO_8859_1 || encodingByte == ID3_TEXT_ENCODING_UTF_8)
        ? 1 : 2;
  }

  private static final class Id3Header {

    private final int majorVersion;
    private final boolean isUnsynchronized;
    private final int framesSize;

    public Id3Header(int majorVersion, boolean isUnsynchronized, int framesSize) {
      this.majorVersion = majorVersion;
      this.isUnsynchronized = isUnsynchronized;
      this.framesSize = framesSize;
    }

  }

}

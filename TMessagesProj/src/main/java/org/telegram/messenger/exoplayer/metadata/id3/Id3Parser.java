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
package org.telegram.messenger.exoplayer.metadata.id3;

import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.metadata.MetadataParser;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Extracts individual TXXX text frames from raw ID3 data.
 */
public final class Id3Parser implements MetadataParser<List<Id3Frame>> {

  private static final int ID3_TEXT_ENCODING_ISO_8859_1 = 0;
  private static final int ID3_TEXT_ENCODING_UTF_16 = 1;
  private static final int ID3_TEXT_ENCODING_UTF_16BE = 2;
  private static final int ID3_TEXT_ENCODING_UTF_8 = 3;

  @Override
  public boolean canParse(String mimeType) {
    return mimeType.equals(MimeTypes.APPLICATION_ID3);
  }

  @Override
  public List<Id3Frame> parse(byte[] data, int size) throws ParserException {
    List<Id3Frame> id3Frames = new ArrayList<>();
    ParsableByteArray id3Data = new ParsableByteArray(data, size);
    int id3Size = parseId3Header(id3Data);

    while (id3Size > 0) {
      int frameId0 = id3Data.readUnsignedByte();
      int frameId1 = id3Data.readUnsignedByte();
      int frameId2 = id3Data.readUnsignedByte();
      int frameId3 = id3Data.readUnsignedByte();
      int frameSize = id3Data.readSynchSafeInt();
      if (frameSize <= 1) {
        break;
      }

      // Skip frame flags.
      id3Data.skipBytes(2);

      try {
        Id3Frame frame;
        if (frameId0 == 'T' && frameId1 == 'X' && frameId2 == 'X' && frameId3 == 'X') {
          frame = parseTxxxFrame(id3Data, frameSize);
        } else if (frameId0 == 'P' && frameId1 == 'R' && frameId2 == 'I' && frameId3 == 'V') {
          frame = parsePrivFrame(id3Data, frameSize);
        } else if (frameId0 == 'G' && frameId1 == 'E' && frameId2 == 'O' && frameId3 == 'B') {
          frame = parseGeobFrame(id3Data, frameSize);
        } else if (frameId0 == 'A' && frameId1 == 'P' && frameId2 == 'I' && frameId3 == 'C') {
          frame = parseApicFrame(id3Data, frameSize);
        } else if (frameId0 == 'T') {
          String id = String.format(Locale.US, "%c%c%c%c", frameId0, frameId1, frameId2, frameId3);
          frame = parseTextInformationFrame(id3Data, frameSize, id);
        } else {
          String id = String.format(Locale.US, "%c%c%c%c", frameId0, frameId1, frameId2, frameId3);
          frame = parseBinaryFrame(id3Data, frameSize, id);
        }
        id3Frames.add(frame);
        id3Size -= frameSize + 10 /* header size */;
      } catch (UnsupportedEncodingException e) {
        throw new ParserException(e);
      }
    }

    return Collections.unmodifiableList(id3Frames);
  }

  private static int indexOfEos(byte[] data, int fromIndex, int encoding) {
    int terminationPos = indexOfZeroByte(data, fromIndex);

    // For single byte encoding charsets, we're done.
    if (encoding == ID3_TEXT_ENCODING_ISO_8859_1 || encoding == ID3_TEXT_ENCODING_UTF_8) {
      return terminationPos;
    }

    // Otherwise look for a second zero byte.
    while (terminationPos < data.length - 1) {
      if (data[terminationPos + 1] == (byte) 0) {
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

  /**
   * Parses an ID3 header.
   *
   * @param id3Buffer A {@link ParsableByteArray} from which data should be read.
   * @return The size of ID3 frames in bytes, excluding the header and footer.
   * @throws ParserException If ID3 file identifier != "ID3".
   */
  private static int parseId3Header(ParsableByteArray id3Buffer) throws ParserException {
    int id1 = id3Buffer.readUnsignedByte();
    int id2 = id3Buffer.readUnsignedByte();
    int id3 = id3Buffer.readUnsignedByte();
    if (id1 != 'I' || id2 != 'D' || id3 != '3') {
      throw new ParserException(String.format(Locale.US,
          "Unexpected ID3 file identifier, expected \"ID3\", actual \"%c%c%c\".", id1, id2, id3));
    }
    id3Buffer.skipBytes(2); // Skip version.

    int flags = id3Buffer.readUnsignedByte();
    int id3Size = id3Buffer.readSynchSafeInt();

    // Check if extended header presents.
    if ((flags & 0x2) != 0) {
      int extendedHeaderSize = id3Buffer.readSynchSafeInt();
      if (extendedHeaderSize > 4) {
        id3Buffer.skipBytes(extendedHeaderSize - 4);
      }
      id3Size -= extendedHeaderSize;
    }

    // Check if footer presents.
    if ((flags & 0x8) != 0) {
      id3Size -= 10;
    }

    return id3Size;
  }

  private static TxxxFrame parseTxxxFrame(ParsableByteArray id3Data, int frameSize)
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

  private static PrivFrame parsePrivFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    byte[] data = new byte[frameSize];
    id3Data.readBytes(data, 0, frameSize);

    int ownerEndIndex = indexOfZeroByte(data, 0);
    String owner = new String(data, 0, ownerEndIndex, "ISO-8859-1");

    int privateDataStartIndex = ownerEndIndex + 1;
    byte[] privateData = Arrays.copyOfRange(data, privateDataStartIndex, data.length);

    return new PrivFrame(owner, privateData);
  }

  private static GeobFrame parseGeobFrame(ParsableByteArray id3Data, int frameSize)
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

  private static ApicFrame parseApicFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int mimeTypeEndIndex = indexOfZeroByte(data, 0);
    String mimeType = new String(data, 0, mimeTypeEndIndex, "ISO-8859-1");

    int pictureType = data[mimeTypeEndIndex + 1] & 0xFF;

    int descriptionStartIndex = mimeTypeEndIndex + 2;
    int descriptionEndIndex = indexOfEos(data, descriptionStartIndex, encoding);
    String description = new String(data, descriptionStartIndex,
        descriptionEndIndex - descriptionStartIndex, charset);

    int pictureDataStartIndex = descriptionEndIndex + delimiterLength(encoding);
    byte[] pictureData = Arrays.copyOfRange(data, pictureDataStartIndex, data.length);

    return new ApicFrame(mimeType, description, pictureType, pictureData);
  }

  private static TextInformationFrame parseTextInformationFrame(ParsableByteArray id3Data,
      int frameSize, String id) throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int descriptionEndIndex = indexOfEos(data, 0, encoding);
    String description = new String(data, 0, descriptionEndIndex, charset);

    return new TextInformationFrame(id, description);
  }

  private static BinaryFrame parseBinaryFrame(ParsableByteArray id3Data, int frameSize, String id) {
    byte[] frame = new byte[frameSize];
    id3Data.readBytes(frame, 0, frameSize);

    return new BinaryFrame(id, frame);
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

}

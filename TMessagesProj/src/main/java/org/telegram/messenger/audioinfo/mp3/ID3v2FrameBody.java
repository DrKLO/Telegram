/*
 * Copyright 2013-2014 Odysseus Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.audioinfo.mp3;

import org.telegram.messenger.audioinfo.util.RangeInputStream;

import java.io.IOException;
import java.io.InputStream;

public class ID3v2FrameBody {

    static final class Buffer {
        byte[] bytes;

        Buffer(int initialLength) {
            bytes = new byte[initialLength];
        }

        byte[] bytes(int minLength) {
            if (minLength > bytes.length) {
                int length = bytes.length * 2;
                while (minLength > length) {
                    length *= 2;
                }
                bytes = new byte[length];
            }
            return bytes;
        }
    }

    static final ThreadLocal<Buffer> textBuffer = new ThreadLocal<Buffer>() {
        @Override
        protected Buffer initialValue() {
            return new Buffer(4096);
        }
    };

    private final RangeInputStream input;
    private final ID3v2TagHeader tagHeader;
    private final ID3v2FrameHeader frameHeader;
    private final ID3v2DataInput data;

    ID3v2FrameBody(InputStream delegate, long position, int dataLength, ID3v2TagHeader tagHeader, ID3v2FrameHeader frameHeader) throws IOException {
        this.input = new RangeInputStream(delegate, position, dataLength);
        this.data = new ID3v2DataInput(input);
        this.tagHeader = tagHeader;
        this.frameHeader = frameHeader;
    }

    public ID3v2DataInput getData() {
        return data;
    }

    public long getPosition() {
        return input.getPosition();
    }

    public long getRemainingLength() {
        return input.getRemainingLength();
    }

    public ID3v2TagHeader getTagHeader() {
        return tagHeader;
    }

    public ID3v2FrameHeader getFrameHeader() {
        return frameHeader;
    }

    private String extractString(byte[] bytes, int offset, int length, ID3v2Encoding encoding, boolean searchZeros) {
        if (searchZeros) {
            int zeros = 0;
            for (int i = 0; i < length; i++) {
                // UTF-16LE may have a zero byte as second byte of a 2-byte character -> skip first zero at odd index
                if (bytes[offset + i] == 0 && (encoding != ID3v2Encoding.UTF_16 || zeros != 0 || (offset + i) % 2 == 0)) {
                    if (++zeros == encoding.getZeroBytes()) {
                        length = i + 1 - encoding.getZeroBytes();
                        break;
                    }
                } else {
                    zeros = 0;
                }
            }
        }
        try {
            String string = new String(bytes, offset, length, encoding.getCharset().name());
            if (string.length() > 0 && string.charAt(0) == '\uFEFF') { // remove BOM
                string = string.substring(1);
            }
            return string;
        } catch (Exception e) {
            return "";
        }
    }

    public String readZeroTerminatedString(int maxLength, ID3v2Encoding encoding) throws IOException, ID3v2Exception {
        int zeros = 0;
        int length = Math.min(maxLength, (int) getRemainingLength());
        byte[] bytes = textBuffer.get().bytes(length);
        for (int i = 0; i < length; i++) {
            // UTF-16LE may have a zero byte as second byte of a 2-byte character -> skip first zero at odd index
            if ((bytes[i] = data.readByte()) == 0 && (encoding != ID3v2Encoding.UTF_16 || zeros != 0 || i % 2 == 0)) {
                if (++zeros == encoding.getZeroBytes()) {
                    return extractString(bytes, 0, i + 1 - encoding.getZeroBytes(), encoding, false);
                }
            } else {
                zeros = 0;
            }
        }
        throw new ID3v2Exception("Could not read zero-termiated string");
    }

    public String readFixedLengthString(int length, ID3v2Encoding encoding) throws IOException, ID3v2Exception {
        if (length > getRemainingLength()) {
            throw new ID3v2Exception("Could not read fixed-length string of length: " + length);
        }
        byte[] bytes = textBuffer.get().bytes(length);
        data.readFully(bytes, 0, length);
        return extractString(bytes, 0, length, encoding, true);
    }

    public ID3v2Encoding readEncoding() throws IOException, ID3v2Exception {
        byte value = data.readByte();
        switch (value) {
            case 0:
                return ID3v2Encoding.ISO_8859_1;
            case 1:
                return ID3v2Encoding.UTF_16;
            case 2:
                return ID3v2Encoding.UTF_16BE;
            case 3:
                return ID3v2Encoding.UTF_8;
            default:
                break;
        }
        throw new ID3v2Exception("Invalid encoding: " + value);
    }

    public String toString() {
        return "id3v2frame[pos=" + getPosition() + ", " + getRemainingLength() + " left]";
    }
}

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

import org.telegram.messenger.audioinfo.AudioInfo;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class ID3v1Info extends AudioInfo {
    public static boolean isID3v1StartPosition(InputStream input) throws IOException {
        input.mark(3);
        try {
            return input.read() == 'T' && input.read() == 'A' && input.read() == 'G';
        } finally {
            input.reset();
        }
    }

    public ID3v1Info(InputStream input) throws IOException {
        if (isID3v1StartPosition(input)) {
            brand = "ID3";
            version = "1.0";
            byte[] bytes = readBytes(input, 128);
            title = extractString(bytes, 3, 30);
            artist = extractString(bytes, 33, 30);
            album = extractString(bytes, 63, 30);
            try {
                year = Short.parseShort(extractString(bytes, 93, 4));
            } catch (NumberFormatException e) {
                year = 0;
            }
            comment = extractString(bytes, 97, 30);
            ID3v1Genre id3v1Genre = ID3v1Genre.getGenre(bytes[127]);
            if (id3v1Genre != null) {
                genre = id3v1Genre.getDescription();
            }
			
			/*
			 * ID3v1.1
			 */
            if (bytes[125] == 0 && bytes[126] != 0) {
                version = "1.1";
                track = (short) (bytes[126] & 0xFF);
            }
        }
    }

    byte[] readBytes(InputStream input, int len) throws IOException {
        int total = 0;
        byte[] bytes = new byte[len];
        while (total < len) {
            int current = input.read(bytes, total, len - total);
            if (current > 0) {
                total += current;
            } else {
                throw new EOFException();
            }
        }
        return bytes;
    }

    String extractString(byte[] bytes, int offset, int length) {
        try {
            String text = new String(bytes, offset, length, "ISO-8859-1");
            int zeroIndex = text.indexOf(0);
            return zeroIndex < 0 ? text : text.substring(0, zeroIndex);
        } catch (Exception e) {
            return "";
        }
    }
}

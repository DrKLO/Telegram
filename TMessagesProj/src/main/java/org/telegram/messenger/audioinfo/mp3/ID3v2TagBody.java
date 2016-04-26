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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

public class ID3v2TagBody {
	private final RangeInputStream input;
	private final ID3v2TagHeader tagHeader;
	private final ID3v2DataInput data;

	ID3v2TagBody(InputStream delegate, long position, int length, ID3v2TagHeader tagHeader) throws IOException {
		this.input = new RangeInputStream(delegate, position, length);
		this.data = new ID3v2DataInput(input);
		this.tagHeader = tagHeader;
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

	public ID3v2FrameBody frameBody(ID3v2FrameHeader frameHeader) throws IOException, ID3v2Exception {
		int dataLength = frameHeader.getBodySize();
		InputStream input = this.input;
		if (frameHeader.isUnsynchronization()) {
			byte[] bytes = data.readFully(frameHeader.getBodySize());
			boolean ff = false;
			int len = 0;
			for (byte b : bytes) {
				if (!ff || b != 0) {
					bytes[len++] = b;
				}
				ff = (b == 0xFF);
			}
			dataLength = len;
			input = new ByteArrayInputStream(bytes, 0, len);
		}
		if (frameHeader.isEncryption()) {
			throw new ID3v2Exception("Frame encryption is not supported");
		}
		if (frameHeader.isCompression()) {
			dataLength = frameHeader.getDataLengthIndicator();
			input = new InflaterInputStream(input);
		}
		return new ID3v2FrameBody(input, frameHeader.getHeaderSize(), dataLength, tagHeader, frameHeader);
	}

	public String toString() {
		return "id3v2tag[pos=" + getPosition() + ", " + getRemainingLength() + " left]";
	}
}

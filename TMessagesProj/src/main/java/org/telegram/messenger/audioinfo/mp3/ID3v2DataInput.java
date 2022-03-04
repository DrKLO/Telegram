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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class ID3v2DataInput {
	private final InputStream input;

	public ID3v2DataInput(InputStream in) {
		this.input = in;
	}

	public final void readFully(byte b[], int off, int len) throws IOException {
		int total = 0;
		while (total < len) {
			int current = input.read(b, off + total, len - total);
			if (current > 0) {
				total += current;
			} else {
				throw new EOFException();
			}
		}
	}

	public byte[] readFully(int len) throws IOException {
		byte[] bytes = new byte[len];
		readFully(bytes, 0, len);
		return bytes;
	}

	public void skipFully(long len) throws IOException {
		long total = 0;
		while (total < len) {
			long current = input.skip(len - total);
			if (current > 0) {
				total += current;
			} else {
				throw new EOFException();
			}
		}
	}

	public byte readByte() throws IOException {
		int b = input.read();
		if (b < 0) {
			throw new EOFException();
		}
		return (byte) b;
	}

	public int readInt() throws IOException {
		return ((readByte() & 0xFF) << 24) | ((readByte() & 0xFF) << 16) | ((readByte() & 0xFF) << 8) | (readByte() & 0xFF);
	}

	public int readSyncsafeInt() throws IOException {
		return ((readByte() & 0x7F) << 21) | ((readByte() & 0x7F) << 14) | ((readByte() & 0x7F) << 7) | (readByte() & 0x7F);
	}
}

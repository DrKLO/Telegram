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

import org.telegram.messenger.audioinfo.util.PositionInputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class MP3Input extends PositionInputStream {
	public MP3Input(InputStream delegate) throws IOException {
		super(delegate);
	}

	public MP3Input(InputStream delegate, long position) {
		super(delegate, position);
	}

	public final void readFully(byte b[], int off, int len) throws IOException {
		int total = 0;
		while (total < len) {
			int current = read(b, off + total, len - total);
			if (current > 0) {
				total += current;
			} else {
				throw new EOFException();
			}
		}
	}

	public void skipFully(long len) throws IOException {
		long total = 0;
		while (total < len) {
			long current = skip(len - total);
			if (current > 0) {
				total += current;
			} else {
				throw new EOFException();
			}
		}
	}

	public String toString() {
		return "mp3[pos=" + getPosition() + "]";
	}
}

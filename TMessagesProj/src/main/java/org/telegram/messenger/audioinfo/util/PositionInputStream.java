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
package org.telegram.messenger.audioinfo.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PositionInputStream extends FilterInputStream {
	private long position;
	private long positionMark;

	public PositionInputStream(InputStream delegate) {
		this(delegate, 0L);
	}

	public PositionInputStream(InputStream delegate, long position) {
		super(delegate);
		this.position = position;
	}

	@Override
	public synchronized void mark(int readlimit) {
		positionMark = position;
		super.mark(readlimit);
	}

	@Override
	public synchronized void reset() throws IOException {
		super.reset();
		position = positionMark;
	}

	public int read() throws IOException {
		int data = super.read();
		if (data >= 0) {
			position++;
		}
		return data;
	}

	public int read(byte[] b, int off, int len) throws IOException {
		long p = position;
		int read = super.read(b, off, len);
		if (read > 0) {
			position = p + read;
		}
		return read;
	}

	@Override
	public final int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	public long skip(long n) throws IOException {
		long p = position;
		long skipped = super.skip(n);
		position = p + skipped;
		return skipped;
	}

	public long getPosition() {
		return position;
	}
}

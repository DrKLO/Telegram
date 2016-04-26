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

import java.nio.charset.Charset;

public enum ID3v2Encoding {
	ISO_8859_1(Charset.forName("ISO-8859-1"), 1),
	UTF_16(Charset.forName("UTF-16"), 2),
	UTF_16BE(Charset.forName("UTF-16BE"), 2),
	UTF_8(Charset.forName("UTF-8"), 1);

	private final Charset charset;
	private final int zeroBytes;

	ID3v2Encoding(Charset charset, int zeroBytes) {
		this.charset = charset;
		this.zeroBytes = zeroBytes;
	}

	public Charset getCharset() {
		return charset;
	}

	public int getZeroBytes() {
		return zeroBytes;
	}
}

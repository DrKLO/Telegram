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

import java.io.IOException;

public class ID3v2FrameHeader {
	private String frameId;
	private int headerSize;
	private int bodySize;
	private boolean unsynchronization;
	private boolean compression;
	private boolean encryption;
	private int dataLengthIndicator;

	public ID3v2FrameHeader(ID3v2TagBody input) throws IOException, ID3v2Exception {
		long startPosition = input.getPosition();

		ID3v2DataInput data = input.getData();
		
		/*
		 * Frame Id
		 */
		if (input.getTagHeader().getVersion() == 2) { // $xx xx xx (three characters)
			frameId = new String(data.readFully(3), "ISO-8859-1");
		} else { // $xx xx xx xx (four characters)
			frameId = new String(data.readFully(4), "ISO-8859-1");
		}
		
		/*
		 * Size 
		 */
		if (input.getTagHeader().getVersion() == 2) { // $xx xx xx
			bodySize = ((data.readByte() & 0xFF) << 16) | ((data.readByte() & 0xFF) << 8) | (data.readByte() & 0xFF);
		} else if (input.getTagHeader().getVersion() == 3) { // $xx xx xx xx
			bodySize = data.readInt();
		} else { // 4 * %0xxxxxxx (sync-save integer)
			bodySize = data.readSyncsafeInt();
		}
		
		/*
		 * Flags
		 */
		if (input.getTagHeader().getVersion() > 2) { // $xx xx
			data.readByte(); // status flags
			byte formatFlags = data.readByte();
			int compressionMask;
			int encryptionMask;
			int groupingIdentityMask;
			int unsynchronizationMask = 0x00;
			int dataLengthIndicatorMask = 0x00;
			if (input.getTagHeader().getVersion() == 3) { // %(compression)(encryption)(groupingIdentity)00000
				compressionMask = 0x80;
				encryptionMask = 0x40;
				groupingIdentityMask = 0x20;
			} else { // %0(groupingIdentity)00(compression)(encryption)(unsynchronization)(dataLengthIndicator)
				groupingIdentityMask = 0x40;
				compressionMask = 0x08;
				encryptionMask = 0x04;
				unsynchronizationMask = 0x02;
				dataLengthIndicatorMask = 0x01;
			}
			compression = (formatFlags & compressionMask) != 0;
			unsynchronization = (formatFlags & unsynchronizationMask) != 0;
			encryption = (formatFlags & encryptionMask) != 0;

			/*
			 * Read flag attachments in the order of the flags (version dependent).
			 */
			if (input.getTagHeader().getVersion() == 3) {
				if (compression) {
					dataLengthIndicator = data.readInt();
					bodySize -= 4;
				}
				if (encryption) {
					data.readByte(); // just skip
					bodySize -= 1;
				}
				if ((formatFlags & groupingIdentityMask) != 0) {
					data.readByte(); // just skip
					bodySize -= 1;
				}
			} else {
				if ((formatFlags & groupingIdentityMask) != 0) {
					data.readByte(); // just skip
					bodySize -= 1;
				}
				if (encryption) {
					data.readByte(); // just skip
					bodySize -= 1;
				}
				if ((formatFlags & dataLengthIndicatorMask) != 0) {
					dataLengthIndicator = data.readSyncsafeInt();
					bodySize -= 4;
				}
			}
		}

		headerSize = (int) (input.getPosition() - startPosition);
	}

	public String getFrameId() {
		return frameId;
	}

	public int getHeaderSize() {
		return headerSize;
	}

	public int getBodySize() {
		return bodySize;
	}

	public boolean isCompression() {
		return compression;
	}

	public boolean isEncryption() {
		return encryption;
	}

	public boolean isUnsynchronization() {
		return unsynchronization;
	}

	public int getDataLengthIndicator() {
		return dataLengthIndicator;
	}

	public boolean isValid() {
		for (int i = 0; i < frameId.length(); i++) {
			if ((frameId.charAt(i) < 'A' || frameId.charAt(i) > 'Z') && (frameId.charAt(i) < '0' || frameId.charAt(i) > '9')) {
				return false;
			}
		}
		return bodySize > 0;
	}

	public boolean isPadding() {
		for (int i = 0; i < frameId.length(); i++) {
			if (frameId.charAt(0) != 0) {
				return false;
			}
		}
		return bodySize == 0;
	}

	@Override
	public String toString() {
		return String.format("%s[id=%s, bodysize=%d]", getClass().getSimpleName(), frameId, bodySize);
	}
}

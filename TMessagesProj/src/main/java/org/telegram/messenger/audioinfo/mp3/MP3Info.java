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
import java.util.logging.Level;
import java.util.logging.Logger;

public class MP3Info extends AudioInfo {
	static final Logger LOGGER = Logger.getLogger(MP3Info.class.getName());

	interface StopReadCondition {
		boolean stopRead(MP3Input data) throws IOException;
	}

	public MP3Info(InputStream input, long fileLength) throws IOException, ID3v2Exception, MP3Exception {
		this(input, fileLength, Level.FINEST);
	}

	public MP3Info(InputStream input, final long fileLength, Level debugLevel) throws IOException, ID3v2Exception, MP3Exception {
		brand = "MP3";
		version = "0";
		MP3Input data = new MP3Input(input);
		if (ID3v2Info.isID3v2StartPosition(data)) {
			ID3v2Info info = new ID3v2Info(data, debugLevel);
			album = info.getAlbum();
			albumArtist = info.getAlbumArtist();
			artist = info.getArtist();
			comment = info.getComment();
			cover = info.getCover();
			smallCover = info.getSmallCover();
			compilation = info.isCompilation();
			composer = info.getComposer();
			copyright = info.getCopyright();
			disc = info.getDisc();
			discs = info.getDiscs();
			duration = info.getDuration();
			genre = info.getGenre();
			grouping = info.getGrouping();
			lyrics = info.getLyrics();
			title = info.getTitle();
			track = info.getTrack();
			tracks = info.getTracks();
			year = info.getYear();
		}
		if (duration <= 0 || duration >= 3600000L) { // don't trust strange durations (e.g. old lame versions always write TLEN 97391548)
			try {
				duration = calculateDuration(data, fileLength, new StopReadCondition() {
					final long stopPosition = fileLength - 128;

					@Override
					public boolean stopRead(MP3Input data) throws IOException {
						return (data.getPosition() == stopPosition) && ID3v1Info.isID3v1StartPosition(data);
					}
				});
			} catch (MP3Exception e) {
				if (LOGGER.isLoggable(debugLevel)) {
					LOGGER.log(debugLevel, "Could not determine MP3 duration", e);
				}
			}
		}
		if (title == null || album == null || artist == null) {
			if (data.getPosition() <= fileLength - 128) { // position to last 128 bytes
				data.skipFully(fileLength - 128 - data.getPosition());
				if (ID3v1Info.isID3v1StartPosition(input)) {
					ID3v1Info info = new ID3v1Info(input);
					if (album == null) {
						album = info.getAlbum();
					}
					if (artist == null) {
						artist = info.getArtist();
					}
					if (comment == null) {
						comment = info.getComment();
					}
					if (genre == null) {
						genre = info.getGenre();
					}
					if (title == null) {
						title = info.getTitle();
					}
					if (track == 0) {
						track = info.getTrack();
					}
					if (year == 0) {
						year = info.getYear();
					}
				}
			}
		}
	}

	MP3Frame readFirstFrame(MP3Input data, StopReadCondition stopCondition) throws IOException {
		int b0 = 0;
		int b1 = stopCondition.stopRead(data) ? -1 : data.read();
		while (b1 != -1) {
			if (b0 == 0xFF && (b1 & 0xE0) == 0xE0) { // first 11 bits should be 1
				data.mark(2); // set mark at b2
				int b2 = stopCondition.stopRead(data) ? -1 : data.read();
				if (b2 == -1) {
					break;
				}
				int b3 = stopCondition.stopRead(data) ? -1 : data.read();
				if (b3 == -1) {
					break;
				}
				MP3Frame.Header header = null;
				try {
					header = new MP3Frame.Header(b1, b2, b3);
				} catch (MP3Exception e) {
					// not a valid frame header
				}
				if (header != null) { // we have a candidate
					/*
					 * The code gets a bit complex here, because we need to be able to reset() to b2 if
					 * the check fails. Thus, we have to reset() to b2 before doing a call to mark().
					 */
					data.reset(); // reset input to b2
					data.mark(header.getFrameSize() + 2); // rest of frame (size - 2) + next header
					/*
					 * read frame data
					 */
					byte[] frameBytes = new byte[header.getFrameSize()];
					frameBytes[0] = (byte) 0xFF;
					frameBytes[1] = (byte) b1;
					try {
						data.readFully(frameBytes, 2, frameBytes.length - 2); // may throw EOFException
					} catch (EOFException e) {
						break;
					}

					MP3Frame frame = new MP3Frame(header, frameBytes);
					/*
					 * read next header  
					 */
					if (!frame.isChecksumError()) {
						int nextB0 = stopCondition.stopRead(data) ? -1 : data.read();
						int nextB1 = stopCondition.stopRead(data) ? -1 : data.read();
						if (nextB0 == -1 || nextB1 == -1) {
							return frame;
						}
						if (nextB0 == 0xFF && (nextB1 & 0xFE) == (b1 & 0xFE)) { // quick check: nextB1 must match b1's version & layer
							int nextB2 = stopCondition.stopRead(data) ? -1 : data.read();
							int nextB3 = stopCondition.stopRead(data) ? -1 : data.read();
							if (nextB2 == -1 || nextB3 == -1) {
								return frame;
							}
							try {
								if (new MP3Frame.Header(nextB1, nextB2, nextB3).isCompatible(header)) {
									data.reset(); // reset input to b2
									data.skipFully(frameBytes.length - 2); // skip to end of frame
									return frame;
								}
							} catch (MP3Exception e) {
								// not a valid frame header
							}
						}
					}
				}

				/*
				 * seems to be a false sync...
				 */
				data.reset(); // reset input to b2
			}

			/*
			 * read next byte
			 */
			b0 = b1;
			b1 = stopCondition.stopRead(data) ? -1 : data.read();
		}
		return null;
	}

	MP3Frame readNextFrame(MP3Input data, StopReadCondition stopCondition, MP3Frame previousFrame) throws IOException {
		MP3Frame.Header previousHeader = previousFrame.getHeader();
		data.mark(4);
		int b0 = stopCondition.stopRead(data) ? -1 : data.read();
		int b1 = stopCondition.stopRead(data) ? -1 : data.read();
		if (b0 == -1 || b1 == -1) {
			return null;
		}
		if (b0 == 0xFF && (b1 & 0xE0) == 0xE0) { // first 11 bits should be 1
			int b2 = stopCondition.stopRead(data) ? -1 : data.read();
			int b3 = stopCondition.stopRead(data) ? -1 : data.read();
			if (b2 == -1 || b3 == -1) {
				return null;
			}
			MP3Frame.Header nextHeader = null;
			try {
				nextHeader = new MP3Frame.Header(b1, b2, b3);
			} catch (MP3Exception e) {
				// not a valid frame header
			}
			if (nextHeader != null && nextHeader.isCompatible(previousHeader)) {
				byte[] frameBytes = new byte[nextHeader.getFrameSize()];
				frameBytes[0] = (byte) b0;
				frameBytes[1] = (byte) b1;
				frameBytes[2] = (byte) b2;
				frameBytes[3] = (byte) b3;
				try {
					data.readFully(frameBytes, 4, frameBytes.length - 4);
				} catch (EOFException e) {
					return null;
				}
				return new MP3Frame(nextHeader, frameBytes);
			}
		}
		data.reset();
		return null;
	}

	long calculateDuration(MP3Input data, long totalLength, StopReadCondition stopCondition) throws IOException, MP3Exception {
		MP3Frame frame = readFirstFrame(data, stopCondition);
		if (frame != null) {
			// check for Xing header
			int numberOfFrames = frame.getNumberOfFrames();
			if (numberOfFrames > 0) { // from Xing/VBRI header
				return frame.getHeader().getTotalDuration(numberOfFrames * frame.getSize());
			} else { // scan file
				numberOfFrames = 1;

				long firstFramePosition = data.getPosition() - frame.getSize();
				long frameSizeSum = frame.getSize();

				int firstFrameBitrate = frame.getHeader().getBitrate();
				long bitrateSum = firstFrameBitrate;
				boolean vbr = false;
				int cbrThreshold = 10000 / frame.getHeader().getDuration(); // assume CBR after 10 seconds

				while (true) {
					if (numberOfFrames == cbrThreshold && !vbr && totalLength > 0) {
						return frame.getHeader().getTotalDuration(totalLength - firstFramePosition);
					}
					if ((frame = readNextFrame(data, stopCondition, frame)) == null) {
						break;
					}
					int bitrate = frame.getHeader().getBitrate();
					if (bitrate != firstFrameBitrate) {
						vbr = true;
					}
					bitrateSum += bitrate;
					frameSizeSum += frame.getSize();
					numberOfFrames++;
				}
				return 1000L * frameSizeSum * numberOfFrames * 8 / bitrateSum;
			}
		} else {
			throw new MP3Exception("No audio frame");
		}
	}
}

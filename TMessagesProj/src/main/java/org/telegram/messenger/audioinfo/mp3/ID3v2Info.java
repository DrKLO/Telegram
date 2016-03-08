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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.audioinfo.AudioInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ID3v2Info extends AudioInfo {
	static final Logger LOGGER = Logger.getLogger(ID3v2Info.class.getName());

	static class AttachedPicture {
		static final byte TYPE_OTHER = 0x00;
		static final byte TYPE_COVER_FRONT = 0x03;

		final byte type;
		final String description;
		final String imageType;
		final byte[] imageData;

		public AttachedPicture(byte type, String description, String imageType, byte[] imageData) {
			this.type = type;
			this.description = description;
			this.imageType = imageType;
			this.imageData = imageData;
		}
	}

	static class CommentOrUnsynchronizedLyrics {
		final String language;
		final String description;
		final String text;

		public CommentOrUnsynchronizedLyrics(String language, String description, String text) {
			this.language = language;
			this.description = description;
			this.text = text;
		}
	}

	public static boolean isID3v2StartPosition(InputStream input) throws IOException {
		input.mark(3);
		try {
			return input.read() == 'I' && input.read() == 'D' && input.read() == '3';
		} finally {
			input.reset();
		}
	}

	private final Level debugLevel;

	private byte coverPictureType;

	public ID3v2Info(InputStream input) throws IOException, ID3v2Exception {
		this(input, Level.FINEST);
	}

	public ID3v2Info(InputStream input, Level debugLevel) throws IOException, ID3v2Exception {
		this.debugLevel = debugLevel;
		if (isID3v2StartPosition(input)) {
			ID3v2TagHeader tagHeader = new ID3v2TagHeader(input);
			brand = "ID3";
			version = String.format("2.%d.%d", tagHeader.getVersion(), tagHeader.getRevision());
			ID3v2TagBody tagBody = tagHeader.tagBody(input);
			try {
				while (tagBody.getRemainingLength() > 10) { // TODO > tag.minimumFrameSize()
					ID3v2FrameHeader frameHeader = new ID3v2FrameHeader(tagBody);
					if (frameHeader.isPadding()) { // we ran into padding
						break;
					}
					if (frameHeader.getBodySize() > tagBody.getRemainingLength()) { // something wrong...
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "ID3 frame claims to extend frames area");
						}
						break;
					}
					if (frameHeader.isValid() && !frameHeader.isEncryption()) {
						ID3v2FrameBody frameBody = tagBody.frameBody(frameHeader);
						try {
							parseFrame(frameBody);
						} catch (ID3v2Exception e) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, String.format("ID3 exception occured in frame %s: %s", frameHeader.getFrameId(), e.getMessage()));
							}
						} finally {
							frameBody.getData().skipFully(frameBody.getRemainingLength());
						}
					} else {
						tagBody.getData().skipFully(frameHeader.getBodySize());
					}
				}
			} catch (ID3v2Exception e) {
				if (LOGGER.isLoggable(debugLevel)) {
					LOGGER.log(debugLevel, "ID3 exception occured: " + e.getMessage());
				}
			}
			tagBody.getData().skipFully(tagBody.getRemainingLength());
			if (tagHeader.getFooterSize() > 0) {
				input.skip(tagHeader.getFooterSize());
			}
		}
	}

	void parseFrame(ID3v2FrameBody frame) throws IOException, ID3v2Exception {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, "Parsing frame: " + frame.getFrameHeader().getFrameId());
		}
		switch (frame.getFrameHeader().getFrameId()) {
			case "PIC":
			case "APIC": // cover: prefer TYPE_COVER_FRONT, then TYPE_OTHER, then anything else
				if (cover == null || coverPictureType != AttachedPicture.TYPE_COVER_FRONT) {
					AttachedPicture picture = parseAttachedPictureFrame(frame);
					if (cover == null || picture.type == AttachedPicture.TYPE_COVER_FRONT || picture.type == AttachedPicture.TYPE_OTHER) {
						try {
							byte[] bytes = picture.imageData;
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inJustDecodeBounds = true;
                            opts.inSampleSize = 1;
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                            if (opts.outWidth > 800 || opts.outHeight > 800) {
                                int size = Math.max(opts.outWidth, opts.outHeight);
                                while (size > 800) {
                                    opts.inSampleSize *= 2;
                                    size /= 2;
                                }
                            }
                            opts.inJustDecodeBounds = false;
                            cover = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                            if (cover != null) {
                                float scale = Math.max(cover.getWidth(), cover.getHeight()) / 120.0f;
                                if (scale > 0) {
                                    smallCover = Bitmap.createScaledBitmap(cover, (int) (cover.getWidth() / scale), (int) (cover.getHeight() / scale), true);
                                } else {
                                    smallCover = cover;
                                }
                                if (smallCover == null) {
                                    smallCover = cover;
                                }
                            }
						} catch (Throwable e) {
							e.printStackTrace();
						}
						coverPictureType = picture.type;
					}
				}
				break;
			case "COM":
			case "COMM":
				CommentOrUnsynchronizedLyrics comm = parseCommentOrUnsynchronizedLyricsFrame(frame);
				if (comment == null || comm.description == null || "".equals(comm.description)) { // prefer "default" comment (without description)
					comment = comm.text;
				}
				break;
			case "TAL":
			case "TALB":
				album = parseTextFrame(frame);
				break;
			case "TCP":
			case "TCMP":
				compilation = "1".equals(parseTextFrame(frame));
				break;
			case "TCM":
			case "TCOM":
				composer = parseTextFrame(frame);
				break;
			case "TCO":
			case "TCON":
				String tcon = parseTextFrame(frame);
				if (tcon.length() > 0) {
					genre = tcon;
					try {
						ID3v1Genre id3v1Genre = null;
						if (tcon.charAt(0) == '(') {
							int pos = tcon.indexOf(')');
							if (pos > 1) { // (123)
								id3v1Genre = ID3v1Genre.getGenre(Integer.parseInt(tcon.substring(1, pos)));
								if (id3v1Genre == null && tcon.length() > pos + 1) { // (789)Special
									genre = tcon.substring(pos + 1);
								}
							}
						} else { // 123
							id3v1Genre = ID3v1Genre.getGenre(Integer.parseInt(tcon));
						}
						if (id3v1Genre != null) {
							genre = id3v1Genre.getDescription();
						}
					} catch (NumberFormatException e) {
						// ignore
					}
				}
				break;
			case "TCR":
			case "TCOP":
				copyright = parseTextFrame(frame);
				break;
			case "TDRC": // v2.4, replaces TYER
				String tdrc = parseTextFrame(frame);
				if (tdrc.length() >= 4) {
					try {
						year = Short.valueOf(tdrc.substring(0, 4));
					} catch (NumberFormatException e) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse year from: " + tdrc);
						}
					}
				}
				break;
			case "TLE":
			case "TLEN":
				String tlen = parseTextFrame(frame);
				try {
					duration = Long.valueOf(tlen);
				} catch (NumberFormatException e) {
					if (LOGGER.isLoggable(debugLevel)) {
						LOGGER.log(debugLevel, "Could not parse track duration: " + tlen);
					}
				}
				break;
			case "TP1":
			case "TPE1":
				artist = parseTextFrame(frame);
				break;
			case "TP2":
			case "TPE2":
				albumArtist = parseTextFrame(frame);
				break;
			case "TPA":
			case "TPOS":
				String tpos = parseTextFrame(frame);
				if (tpos.length() > 0) {
					int index = tpos.indexOf('/');
					if (index < 0) {
						try {
							disc = Short.valueOf(tpos);
						} catch (NumberFormatException e) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse disc number: " + tpos);
							}
						}
					} else {
						try {
							disc = Short.valueOf(tpos.substring(0, index));
						} catch (NumberFormatException e) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse disc number: " + tpos);
							}
						}
						try {
							discs = Short.valueOf(tpos.substring(index + 1));
						} catch (NumberFormatException e) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse number of discs: " + tpos);
							}
						}
					}
				}
				break;
			case "TRK":
			case "TRCK":
				String trck = parseTextFrame(frame);
				if (trck.length() > 0) {
					int index = trck.indexOf('/');
					if (index < 0) {
						try {
							track = Short.valueOf(trck);
						} catch (NumberFormatException e) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse track number: " + trck);
							}
						}
					} else {
						try {
							track = Short.valueOf(trck.substring(0, index));
						} catch (NumberFormatException e) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse track number: " + trck);
							}
						}
						try {
							tracks = Short.valueOf(trck.substring(index + 1));
						} catch (NumberFormatException e) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse number of tracks: " + trck);
							}
						}
					}
				}
				break;
			case "TT1":
			case "TIT1":
				grouping = parseTextFrame(frame);
				break;
			case "TT2":
			case "TIT2":
				title = parseTextFrame(frame);
				break;
			case "TYE":
			case "TYER":
				String tyer = parseTextFrame(frame);
				if (tyer.length() > 0) {
					try {
						year = Short.valueOf(tyer);
					} catch (NumberFormatException e) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse year: " + tyer);
						}
					}
				}
				break;
			case "ULT":
			case "USLT":
				if (lyrics == null) {
					lyrics = parseCommentOrUnsynchronizedLyricsFrame(frame).text;
				}
				break;
			default:
				break;
		}
	}

	String parseTextFrame(ID3v2FrameBody frame) throws IOException, ID3v2Exception {
		ID3v2Encoding encoding = frame.readEncoding();
		return frame.readFixedLengthString((int) frame.getRemainingLength(), encoding);
	}

	CommentOrUnsynchronizedLyrics parseCommentOrUnsynchronizedLyricsFrame(ID3v2FrameBody data) throws IOException, ID3v2Exception {
		ID3v2Encoding encoding = data.readEncoding();
		String language = data.readFixedLengthString(3, ID3v2Encoding.ISO_8859_1);
		String description = data.readZeroTerminatedString(200, encoding);
		String text = data.readFixedLengthString((int) data.getRemainingLength(), encoding);
		return new CommentOrUnsynchronizedLyrics(language, description, text);
	}

	AttachedPicture parseAttachedPictureFrame(ID3v2FrameBody data) throws IOException, ID3v2Exception {
		ID3v2Encoding encoding = data.readEncoding();
		String imageType;
		if (data.getTagHeader().getVersion() == 2) { // file type, e.g. "JPG"
			String fileType = data.readFixedLengthString(3, ID3v2Encoding.ISO_8859_1);
			switch (fileType.toUpperCase()) {
				case "PNG":
					imageType = "image/png";
					break;
				case "JPG":
					imageType = "image/jpeg";
					break;
				default:
					imageType = "image/unknown";
			}
		} else { // mime type, e.g. "image/jpeg"
			imageType = data.readZeroTerminatedString(20, ID3v2Encoding.ISO_8859_1);
		}
		byte pictureType = data.getData().readByte();
		String description = data.readZeroTerminatedString(200, encoding);
		byte[] imageData = data.getData().readFully((int) data.getRemainingLength());
		return new AttachedPicture(pictureType, description, imageType, imageData);
	}
}

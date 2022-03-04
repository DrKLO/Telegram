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
package org.telegram.messenger.audioinfo.m4a;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.audioinfo.mp3.ID3v1Genre;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

public class M4AInfo extends AudioInfo {
	static final Logger LOGGER = Logger.getLogger(M4AInfo.class.getName());

	private static final String ASCII = "ISO8859_1";
	private static final String UTF_8 = "UTF-8";

	private BigDecimal volume;        // normal = 1.0
	private BigDecimal speed;        // normal = 1.0

	private short tempo;
	private byte rating;            // none = 0, clean = 2, explicit = 4

	private final Level debugLevel;

	public M4AInfo(InputStream input) throws IOException {
		this(input, Level.FINEST);
	}

	public M4AInfo(InputStream input, Level debugLevel) throws IOException {
		this.debugLevel = debugLevel;
		MP4Input mp4 = new MP4Input(input);
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, mp4.toString());
		}
		ftyp(mp4.nextChild("ftyp"));
		moov(mp4.nextChildUpTo("moov"));
	}

	void ftyp(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		brand = atom.readString(4, ASCII).trim();
		if (brand.matches("M4V|MP4|mp42|isom")) { // experimental file types
			LOGGER.warning(atom.getPath() + ": brand=" + brand + " (experimental)");
		} else if (!brand.matches("M4A|M4P")) {
			LOGGER.warning(atom.getPath() + ": brand=" + brand + " (expected M4A or M4P)");
		}
		version = String.valueOf(atom.readInt());
	}

	void moov(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		while (atom.hasMoreChildren()) {
			MP4Atom child = atom.nextChild();
			switch (child.getType()) {
				case "mvhd":
					mvhd(child);
					break;
				case "trak":
					trak(child);
					break;
				case "udta":
					udta(child);
					break;
				default:
					break;
			}
		}
	}

	void mvhd(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		byte version = atom.readByte();
		atom.skip(3); // flags
		atom.skip(version == 1 ? 16 : 8); // created/modified date
		int scale = atom.readInt();
		long units = version == 1 ? atom.readLong() : atom.readInt();
		if (duration == 0) {
			duration = 1000 * units / scale;
		} else if (LOGGER.isLoggable(debugLevel) && Math.abs(duration - 1000 * units / scale) > 2) {
			LOGGER.log(debugLevel, "mvhd: duration " + duration + " -> " + (1000 * units / scale));
		}
		speed = atom.readIntegerFixedPoint();
		volume = atom.readShortFixedPoint();
	}

	void trak(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		mdia(atom.nextChildUpTo("mdia"));
	}

	void mdia(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		mdhd(atom.nextChild("mdhd"));
	}

	void mdhd(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		byte version = atom.readByte();
		atom.skip(3);
		atom.skip(version == 1 ? 16 : 8); // created/modified date
		int sampleRate = atom.readInt();
		long samples = version == 1 ? atom.readLong() : atom.readInt();
		if (duration == 0) {
			duration = 1000 * samples / sampleRate;
		} else if (LOGGER.isLoggable(debugLevel) && Math.abs(duration - 1000 * samples / sampleRate) > 2) {
			LOGGER.log(debugLevel, "mdhd: duration " + duration + " -> " + (1000 * samples / sampleRate));
		}
	}

	void udta(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		while (atom.hasMoreChildren()) {
			MP4Atom child = atom.nextChild();
			if ("meta".equals(child.getType())) {
				meta(child);
				break;
			}
		}
	}

	void meta(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		atom.skip(4); // version/flags
		while (atom.hasMoreChildren()) {
			MP4Atom child = atom.nextChild();
			if ("ilst".equals(child.getType())) {
				ilst(child);
				break;
			}
		}
	}

	void ilst(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		while (atom.hasMoreChildren()) {
			MP4Atom child = atom.nextChild();
			if (LOGGER.isLoggable(debugLevel)) {
				LOGGER.log(debugLevel, child.toString());
			}
			if (child.getRemaining() == 0) {
				if (LOGGER.isLoggable(debugLevel)) {
					LOGGER.log(debugLevel, child.getPath() + ": contains no value");
				}
				continue;
			}
			data(child.nextChildUpTo("data"));
		}
	}

	void data(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		atom.skip(4); // version & flags
		atom.skip(4); // reserved
		switch (atom.getParent().getType()) {
			case "©alb":
				album = atom.readString(UTF_8);
				break;
			case "aART":
				albumArtist = atom.readString(UTF_8);
				break;
			case "©ART":
				artist = atom.readString(UTF_8);
				break;
			case "©cmt":
				comment = atom.readString(UTF_8);
				break;
			case "©com":
			case "©wrt":
				if (composer == null || composer.trim().length() == 0) {
					composer = atom.readString(UTF_8);
				}
				break;
			case "covr":
                try {
                    byte[] bytes = atom.readBytes();
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
				break;
			case "cpil":
				compilation = atom.readBoolean();
				break;
			case "cprt":
			case "©cpy":
				if (copyright == null || copyright.trim().length() == 0) {
					copyright = atom.readString(UTF_8);
				}
				break;
			case "©day":
				String day = atom.readString(UTF_8).trim();
				if (day.length() >= 4) {
					try {
						year = Short.valueOf(day.substring(0, 4));
					} catch (NumberFormatException e) {
						// ignore
					}
				}
				break;
			case "disk":
				atom.skip(2); // padding?
				disc = atom.readShort();
				discs = atom.readShort();
				break;
			case "gnre":
				if (genre == null || genre.trim().length() == 0) {
					if (atom.getRemaining() == 2) { // id3v1 genre?
						int index = atom.readShort() - 1;
						ID3v1Genre id3v1Genre = ID3v1Genre.getGenre(index);
						if (id3v1Genre != null) {
							genre = id3v1Genre.getDescription();
						}
					} else {
						genre = atom.readString(UTF_8);
					}
				}
				break;
			case "©gen":
				if (genre == null || genre.trim().length() == 0) {
					genre = atom.readString(UTF_8);
				}
				break;
			case "©grp":
				grouping = atom.readString(UTF_8);
				break;
			case "©lyr":
				lyrics = atom.readString(UTF_8);
				break;
			case "©nam":
				title = atom.readString(UTF_8);
				break;
			case "rtng":
				rating = atom.readByte();
				break;
			case "tmpo":
				tempo = atom.readShort();
				break;
			case "trkn":
				atom.skip(2); // padding?
				track = atom.readShort();
				tracks = atom.readShort();
				break;
			default:
				break;
		}
	}

	public short getTempo() {
		return tempo;
	}

	public byte getRating() {
		return rating;
	}

	public BigDecimal getSpeed() {
		return speed;
	}

	public BigDecimal getVolume() {
		return volume;
	}
}

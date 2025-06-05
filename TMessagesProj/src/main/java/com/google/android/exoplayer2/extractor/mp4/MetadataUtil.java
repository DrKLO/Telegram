/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.extractor.mp4;

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.GaplessInfoHolder;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.metadata.id3.CommentFrame;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.metadata.id3.InternalFrame;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.metadata.mp4.MdtaMetadataEntry;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Utilities for handling metadata in MP4. */
/* package */ final class MetadataUtil {

  private static final String TAG = "MetadataUtil";

  // Codes that start with the copyright character (omitted) and have equivalent ID3 frames.
  private static final int SHORT_TYPE_NAME_1 = 0x006e616d;
  private static final int SHORT_TYPE_NAME_2 = 0x0074726b;
  private static final int SHORT_TYPE_COMMENT = 0x00636d74;
  private static final int SHORT_TYPE_YEAR = 0x00646179;
  private static final int SHORT_TYPE_ARTIST = 0x00415254;
  private static final int SHORT_TYPE_ENCODER = 0x00746f6f;
  private static final int SHORT_TYPE_ALBUM = 0x00616c62;
  private static final int SHORT_TYPE_COMPOSER_1 = 0x00636f6d;
  private static final int SHORT_TYPE_COMPOSER_2 = 0x00777274;
  private static final int SHORT_TYPE_LYRICS = 0x006c7972;
  private static final int SHORT_TYPE_GENRE = 0x0067656e;

  // Codes that have equivalent ID3 frames.
  private static final int TYPE_COVER_ART = 0x636f7672;
  private static final int TYPE_GENRE = 0x676e7265;
  private static final int TYPE_GROUPING = 0x00677270;
  private static final int TYPE_DISK_NUMBER = 0x6469736b;
  private static final int TYPE_TRACK_NUMBER = 0x74726b6e;
  private static final int TYPE_TEMPO = 0x746d706f;
  private static final int TYPE_COMPILATION = 0x6370696c;
  private static final int TYPE_ALBUM_ARTIST = 0x61415254;
  private static final int TYPE_SORT_TRACK_NAME = 0x736f6e6d;
  private static final int TYPE_SORT_ALBUM = 0x736f616c;
  private static final int TYPE_SORT_ARTIST = 0x736f6172;
  private static final int TYPE_SORT_ALBUM_ARTIST = 0x736f6161;
  private static final int TYPE_SORT_COMPOSER = 0x736f636f;

  // Types that do not have equivalent ID3 frames.
  private static final int TYPE_RATING = 0x72746e67;
  private static final int TYPE_GAPLESS_ALBUM = 0x70676170;
  private static final int TYPE_TV_SORT_SHOW = 0x736f736e;
  private static final int TYPE_TV_SHOW = 0x74767368;

  // Type for items that are intended for internal use by the player.
  private static final int TYPE_INTERNAL = 0x2d2d2d2d;

  private static final int PICTURE_TYPE_FRONT_COVER = 3;

  // Standard genres.
  @VisibleForTesting
  /* package */ static final String[] STANDARD_GENRES =
      new String[] {
        // These are the official ID3v1 genres.
        "Blues",
        "Classic Rock",
        "Country",
        "Dance",
        "Disco",
        "Funk",
        "Grunge",
        "Hip-Hop",
        "Jazz",
        "Metal",
        "New Age",
        "Oldies",
        "Other",
        "Pop",
        "R&B",
        "Rap",
        "Reggae",
        "Rock",
        "Techno",
        "Industrial",
        "Alternative",
        "Ska",
        "Death Metal",
        "Pranks",
        "Soundtrack",
        "Euro-Techno",
        "Ambient",
        "Trip-Hop",
        "Vocal",
        "Jazz+Funk",
        "Fusion",
        "Trance",
        "Classical",
        "Instrumental",
        "Acid",
        "House",
        "Game",
        "Sound Clip",
        "Gospel",
        "Noise",
        "AlternRock",
        "Bass",
        "Soul",
        "Punk",
        "Space",
        "Meditative",
        "Instrumental Pop",
        "Instrumental Rock",
        "Ethnic",
        "Gothic",
        "Darkwave",
        "Techno-Industrial",
        "Electronic",
        "Pop-Folk",
        "Eurodance",
        "Dream",
        "Southern Rock",
        "Comedy",
        "Cult",
        "Gangsta",
        "Top 40",
        "Christian Rap",
        "Pop/Funk",
        "Jungle",
        "Native American",
        "Cabaret",
        "New Wave",
        "Psychadelic",
        "Rave",
        "Showtunes",
        "Trailer",
        "Lo-Fi",
        "Tribal",
        "Acid Punk",
        "Acid Jazz",
        "Polka",
        "Retro",
        "Musical",
        "Rock & Roll",
        "Hard Rock",
        // Genres made up by the authors of Winamp (v1.91) and later added to the ID3 spec.
        "Folk",
        "Folk-Rock",
        "National Folk",
        "Swing",
        "Fast Fusion",
        "Bebob",
        "Latin",
        "Revival",
        "Celtic",
        "Bluegrass",
        "Avantgarde",
        "Gothic Rock",
        "Progressive Rock",
        "Psychedelic Rock",
        "Symphonic Rock",
        "Slow Rock",
        "Big Band",
        "Chorus",
        "Easy Listening",
        "Acoustic",
        "Humour",
        "Speech",
        "Chanson",
        "Opera",
        "Chamber Music",
        "Sonata",
        "Symphony",
        "Booty Bass",
        "Primus",
        "Porn Groove",
        "Satire",
        "Slow Jam",
        "Club",
        "Tango",
        "Samba",
        "Folklore",
        "Ballad",
        "Power Ballad",
        "Rhythmic Soul",
        "Freestyle",
        "Duet",
        "Punk Rock",
        "Drum Solo",
        "A capella",
        "Euro-House",
        "Dance Hall",
        // Genres made up by the authors of Winamp (v1.91) but have not been added to the ID3 spec.
        "Goa",
        "Drum & Bass",
        "Club-House",
        "Hardcore",
        "Terror",
        "Indie",
        "BritPop",
        "Afro-Punk",
        "Polsk Punk",
        "Beat",
        "Christian Gangsta Rap",
        "Heavy Metal",
        "Black Metal",
        "Crossover",
        "Contemporary Christian",
        "Christian Rock",
        "Merengue",
        "Salsa",
        "Thrash Metal",
        "Anime",
        "Jpop",
        "Synthpop",
        // Genres made up by the authors of Winamp (v5.6) but have not been added to the ID3 spec.
        "Abstract",
        "Art Rock",
        "Baroque",
        "Bhangra",
        "Big beat",
        "Breakbeat",
        "Chillout",
        "Downtempo",
        "Dub",
        "EBM",
        "Eclectic",
        "Electro",
        "Electroclash",
        "Emo",
        "Experimental",
        "Garage",
        "Global",
        "IDM",
        "Illbient",
        "Industro-Goth",
        "Jam Band",
        "Krautrock",
        "Leftfield",
        "Lounge",
        "Math Rock",
        "New Romantic",
        "Nu-Breakz",
        "Post-Punk",
        "Post-Rock",
        "Psytrance",
        "Shoegaze",
        "Space Rock",
        "Trop Rock",
        "World Music",
        "Neoclassical",
        "Audiobook",
        "Audio theatre",
        "Neue Deutsche Welle",
        "Podcast",
        "Indie-Rock",
        "G-Funk",
        "Dubstep",
        "Garage Rock",
        "Psybient"
      };

  private static final int TYPE_TOP_BYTE_COPYRIGHT = 0xA9;
  private static final int TYPE_TOP_BYTE_REPLACEMENT = 0xFD; // Truncated value of \uFFFD.

  private MetadataUtil() {}

  /** Updates a {@link Format.Builder} to include metadata from the provided sources. */
  public static void setFormatMetadata(
      int trackType,
      @Nullable Metadata udtaMetaMetadata,
      @Nullable Metadata mdtaMetadata,
      Format.Builder formatBuilder,
      @NullableType Metadata... additionalMetadata) {
    Metadata formatMetadata = new Metadata();

    if (trackType == C.TRACK_TYPE_AUDIO) {
      // We assume all meta metadata in the udta box is associated with the audio track.
      if (udtaMetaMetadata != null) {
        formatMetadata = udtaMetaMetadata;
      }
    } else if (trackType == C.TRACK_TYPE_VIDEO) {
      // Populate only metadata keys that are known to be specific to video.
      if (mdtaMetadata != null) {
        for (int i = 0; i < mdtaMetadata.length(); i++) {
          Metadata.Entry entry = mdtaMetadata.get(i);
          if (entry instanceof MdtaMetadataEntry) {
            MdtaMetadataEntry mdtaMetadataEntry = (MdtaMetadataEntry) entry;
            if (MdtaMetadataEntry.KEY_ANDROID_CAPTURE_FPS.equals(mdtaMetadataEntry.key)) {
              formatMetadata = new Metadata(mdtaMetadataEntry);
              break;
            }
          }
        }
      }
    }

    for (Metadata metadata : additionalMetadata) {
      formatMetadata = formatMetadata.copyWithAppendedEntriesFrom(metadata);
    }

    if (formatMetadata.length() > 0) {
      formatBuilder.setMetadata(formatMetadata);
    }
  }

  /**
   * Updates a {@link Format.Builder} to include audio gapless information from the provided source.
   */
  public static void setFormatGaplessInfo(
      int trackType, GaplessInfoHolder gaplessInfoHolder, Format.Builder formatBuilder) {
    if (trackType == C.TRACK_TYPE_AUDIO && gaplessInfoHolder.hasGaplessInfo()) {
      formatBuilder
          .setEncoderDelay(gaplessInfoHolder.encoderDelay)
          .setEncoderPadding(gaplessInfoHolder.encoderPadding);
    }
  }

  /**
   * Parses a single userdata ilst element from a {@link ParsableByteArray}. The element is read
   * starting from the current position of the {@link ParsableByteArray}, and the position is
   * advanced by the size of the element. The position is advanced even if the element's type is
   * unrecognized.
   *
   * @param ilst Holds the data to be parsed.
   * @return The parsed element, or null if the element's type was not recognized.
   */
  @Nullable
  public static Metadata.Entry parseIlstElement(ParsableByteArray ilst) {
    int position = ilst.getPosition();
    int endPosition = position + ilst.readInt();
    int type = ilst.readInt();
    int typeTopByte = (type >> 24) & 0xFF;
    try {
      if (typeTopByte == TYPE_TOP_BYTE_COPYRIGHT || typeTopByte == TYPE_TOP_BYTE_REPLACEMENT) {
        int shortType = type & 0x00FFFFFF;
        if (shortType == SHORT_TYPE_COMMENT) {
          return parseCommentAttribute(type, ilst);
        } else if (shortType == SHORT_TYPE_NAME_1 || shortType == SHORT_TYPE_NAME_2) {
          return parseTextAttribute(type, "TIT2", ilst);
        } else if (shortType == SHORT_TYPE_COMPOSER_1 || shortType == SHORT_TYPE_COMPOSER_2) {
          return parseTextAttribute(type, "TCOM", ilst);
        } else if (shortType == SHORT_TYPE_YEAR) {
          return parseTextAttribute(type, "TDRC", ilst);
        } else if (shortType == SHORT_TYPE_ARTIST) {
          return parseTextAttribute(type, "TPE1", ilst);
        } else if (shortType == SHORT_TYPE_ENCODER) {
          return parseTextAttribute(type, "TSSE", ilst);
        } else if (shortType == SHORT_TYPE_ALBUM) {
          return parseTextAttribute(type, "TALB", ilst);
        } else if (shortType == SHORT_TYPE_LYRICS) {
          return parseTextAttribute(type, "USLT", ilst);
        } else if (shortType == SHORT_TYPE_GENRE) {
          return parseTextAttribute(type, "TCON", ilst);
        } else if (shortType == TYPE_GROUPING) {
          return parseTextAttribute(type, "TIT1", ilst);
        }
      } else if (type == TYPE_GENRE) {
        return parseStandardGenreAttribute(ilst);
      } else if (type == TYPE_DISK_NUMBER) {
        return parseIndexAndCountAttribute(type, "TPOS", ilst);
      } else if (type == TYPE_TRACK_NUMBER) {
        return parseIndexAndCountAttribute(type, "TRCK", ilst);
      } else if (type == TYPE_TEMPO) {
        return parseUint8Attribute(type, "TBPM", ilst, true, false);
      } else if (type == TYPE_COMPILATION) {
        return parseUint8Attribute(type, "TCMP", ilst, true, true);
      } else if (type == TYPE_COVER_ART) {
        return parseCoverArt(ilst);
      } else if (type == TYPE_ALBUM_ARTIST) {
        return parseTextAttribute(type, "TPE2", ilst);
      } else if (type == TYPE_SORT_TRACK_NAME) {
        return parseTextAttribute(type, "TSOT", ilst);
      } else if (type == TYPE_SORT_ALBUM) {
        return parseTextAttribute(type, "TSO2", ilst);
      } else if (type == TYPE_SORT_ARTIST) {
        return parseTextAttribute(type, "TSOA", ilst);
      } else if (type == TYPE_SORT_ALBUM_ARTIST) {
        return parseTextAttribute(type, "TSOP", ilst);
      } else if (type == TYPE_SORT_COMPOSER) {
        return parseTextAttribute(type, "TSOC", ilst);
      } else if (type == TYPE_RATING) {
        return parseUint8Attribute(type, "ITUNESADVISORY", ilst, false, false);
      } else if (type == TYPE_GAPLESS_ALBUM) {
        return parseUint8Attribute(type, "ITUNESGAPLESS", ilst, false, true);
      } else if (type == TYPE_TV_SORT_SHOW) {
        return parseTextAttribute(type, "TVSHOWSORT", ilst);
      } else if (type == TYPE_TV_SHOW) {
        return parseTextAttribute(type, "TVSHOW", ilst);
      } else if (type == TYPE_INTERNAL) {
        return parseInternalAttribute(ilst, endPosition);
      }
      Log.d(TAG, "Skipped unknown metadata entry: " + Atom.getAtomTypeString(type));
      return null;
    } finally {
      ilst.setPosition(endPosition);
    }
  }

  /**
   * Parses an 'mdta' metadata entry starting at the current position in an ilst box.
   *
   * @param ilst The ilst box.
   * @param endPosition The end position of the entry in the ilst box.
   * @param key The mdta metadata entry key for the entry.
   * @return The parsed element, or null if the entry wasn't recognized.
   */
  @Nullable
  public static MdtaMetadataEntry parseMdtaMetadataEntryFromIlst(
      ParsableByteArray ilst, int endPosition, String key) {
    int atomPosition;
    while ((atomPosition = ilst.getPosition()) < endPosition) {
      int atomSize = ilst.readInt();
      int atomType = ilst.readInt();
      if (atomType == Atom.TYPE_data) {
        int typeIndicator = ilst.readInt();
        int localeIndicator = ilst.readInt();
        int dataSize = atomSize - 16;
        byte[] value = new byte[dataSize];
        ilst.readBytes(value, 0, dataSize);
        return new MdtaMetadataEntry(key, value, localeIndicator, typeIndicator);
      }
      ilst.setPosition(atomPosition + atomSize);
    }
    return null;
  }

  @Nullable
  private static TextInformationFrame parseTextAttribute(
      int type, String id, ParsableByteArray data) {
    int atomSize = data.readInt();
    int atomType = data.readInt();
    if (atomType == Atom.TYPE_data) {
      data.skipBytes(8); // version (1), flags (3), empty (4)
      String value = data.readNullTerminatedString(atomSize - 16);
      return new TextInformationFrame(id, /* description= */ null, ImmutableList.of(value));
    }
    Log.w(TAG, "Failed to parse text attribute: " + Atom.getAtomTypeString(type));
    return null;
  }

  @Nullable
  private static CommentFrame parseCommentAttribute(int type, ParsableByteArray data) {
    int atomSize = data.readInt();
    int atomType = data.readInt();
    if (atomType == Atom.TYPE_data) {
      data.skipBytes(8); // version (1), flags (3), empty (4)
      String value = data.readNullTerminatedString(atomSize - 16);
      return new CommentFrame(C.LANGUAGE_UNDETERMINED, value, value);
    }
    Log.w(TAG, "Failed to parse comment attribute: " + Atom.getAtomTypeString(type));
    return null;
  }

  @Nullable
  private static Id3Frame parseUint8Attribute(
      int type,
      String id,
      ParsableByteArray data,
      boolean isTextInformationFrame,
      boolean isBoolean) {
    int value = parseUint8AttributeValue(data);
    if (isBoolean) {
      value = min(1, value);
    }
    if (value >= 0) {
      return isTextInformationFrame
          ? new TextInformationFrame(
              id, /* description= */ null, ImmutableList.of(Integer.toString(value)))
          : new CommentFrame(C.LANGUAGE_UNDETERMINED, id, Integer.toString(value));
    }
    Log.w(TAG, "Failed to parse uint8 attribute: " + Atom.getAtomTypeString(type));
    return null;
  }

  @Nullable
  private static TextInformationFrame parseIndexAndCountAttribute(
      int type, String attributeName, ParsableByteArray data) {
    int atomSize = data.readInt();
    int atomType = data.readInt();
    if (atomType == Atom.TYPE_data && atomSize >= 22) {
      data.skipBytes(10); // version (1), flags (3), empty (4), empty (2)
      int index = data.readUnsignedShort();
      if (index > 0) {
        String value = "" + index;
        int count = data.readUnsignedShort();
        if (count > 0) {
          value += "/" + count;
        }
        return new TextInformationFrame(
            attributeName, /* description= */ null, ImmutableList.of(value));
      }
    }
    Log.w(TAG, "Failed to parse index/count attribute: " + Atom.getAtomTypeString(type));
    return null;
  }

  @Nullable
  private static TextInformationFrame parseStandardGenreAttribute(ParsableByteArray data) {
    int genreCode = parseUint8AttributeValue(data);
    @Nullable
    String genreString =
        (0 < genreCode && genreCode <= STANDARD_GENRES.length)
            ? STANDARD_GENRES[genreCode - 1]
            : null;
    if (genreString != null) {
      return new TextInformationFrame(
          "TCON", /* description= */ null, ImmutableList.of(genreString));
    }
    Log.w(TAG, "Failed to parse standard genre code");
    return null;
  }

  @Nullable
  private static ApicFrame parseCoverArt(ParsableByteArray data) {
    int atomSize = data.readInt();
    int atomType = data.readInt();
    if (atomType == Atom.TYPE_data) {
      int fullVersionInt = data.readInt();
      int flags = Atom.parseFullAtomFlags(fullVersionInt);
      @Nullable String mimeType = flags == 13 ? "image/jpeg" : flags == 14 ? "image/png" : null;
      if (mimeType == null) {
        Log.w(TAG, "Unrecognized cover art flags: " + flags);
        return null;
      }
      data.skipBytes(4); // empty (4)
      byte[] pictureData = new byte[atomSize - 16];
      data.readBytes(pictureData, 0, pictureData.length);
      return new ApicFrame(
          mimeType,
          /* description= */ null,
          /* pictureType= */ PICTURE_TYPE_FRONT_COVER,
          pictureData);
    }
    Log.w(TAG, "Failed to parse cover art attribute");
    return null;
  }

  @Nullable
  private static Id3Frame parseInternalAttribute(ParsableByteArray data, int endPosition) {
    @Nullable String domain = null;
    @Nullable String name = null;
    int dataAtomPosition = -1;
    int dataAtomSize = -1;
    while (data.getPosition() < endPosition) {
      int atomPosition = data.getPosition();
      int atomSize = data.readInt();
      int atomType = data.readInt();
      data.skipBytes(4); // version (1), flags (3)
      if (atomType == Atom.TYPE_mean) {
        domain = data.readNullTerminatedString(atomSize - 12);
      } else if (atomType == Atom.TYPE_name) {
        name = data.readNullTerminatedString(atomSize - 12);
      } else {
        if (atomType == Atom.TYPE_data) {
          dataAtomPosition = atomPosition;
          dataAtomSize = atomSize;
        }
        data.skipBytes(atomSize - 12);
      }
    }
    if (domain == null || name == null || dataAtomPosition == -1) {
      return null;
    }
    data.setPosition(dataAtomPosition);
    data.skipBytes(16); // size (4), type (4), version (1), flags (3), empty (4)
    String value = data.readNullTerminatedString(dataAtomSize - 16);
    return new InternalFrame(domain, name, value);
  }

  private static int parseUint8AttributeValue(ParsableByteArray data) {
    data.skipBytes(4); // atomSize
    int atomType = data.readInt();
    if (atomType == Atom.TYPE_data) {
      data.skipBytes(8); // version (1), flags (3), empty (4)
      return data.readUnsignedByte();
    }
    Log.w(TAG, "Failed to parse uint8 attribute value");
    return -1;
  }
}

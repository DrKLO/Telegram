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
package org.telegram.messenger.exoplayer2.source.hls.playlist;

import android.net.Uri;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.source.UnrecognizedInputFormatException;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import org.telegram.messenger.exoplayer2.upstream.ParsingLoadable;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HLS playlists parsing logic.
 */
public final class HlsPlaylistParser implements ParsingLoadable.Parser<HlsPlaylist> {

  private static final String PLAYLIST_HEADER = "#EXTM3U";

  private static final String TAG_VERSION = "#EXT-X-VERSION";
  private static final String TAG_PLAYLIST_TYPE = "#EXT-X-PLAYLIST-TYPE";
  private static final String TAG_STREAM_INF = "#EXT-X-STREAM-INF";
  private static final String TAG_MEDIA = "#EXT-X-MEDIA";
  private static final String TAG_TARGET_DURATION = "#EXT-X-TARGETDURATION";
  private static final String TAG_DISCONTINUITY = "#EXT-X-DISCONTINUITY";
  private static final String TAG_DISCONTINUITY_SEQUENCE = "#EXT-X-DISCONTINUITY-SEQUENCE";
  private static final String TAG_PROGRAM_DATE_TIME = "#EXT-X-PROGRAM-DATE-TIME";
  private static final String TAG_INIT_SEGMENT = "#EXT-X-MAP";
  private static final String TAG_MEDIA_DURATION = "#EXTINF";
  private static final String TAG_MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE";
  private static final String TAG_START = "#EXT-X-START";
  private static final String TAG_ENDLIST = "#EXT-X-ENDLIST";
  private static final String TAG_KEY = "#EXT-X-KEY";
  private static final String TAG_BYTERANGE = "#EXT-X-BYTERANGE";

  private static final String TYPE_AUDIO = "AUDIO";
  private static final String TYPE_VIDEO = "VIDEO";
  private static final String TYPE_SUBTITLES = "SUBTITLES";
  private static final String TYPE_CLOSED_CAPTIONS = "CLOSED-CAPTIONS";

  private static final String METHOD_NONE = "NONE";
  private static final String METHOD_AES128 = "AES-128";

  private static final String BOOLEAN_TRUE = "YES";
  private static final String BOOLEAN_FALSE = "NO";

  private static final Pattern REGEX_BANDWIDTH = Pattern.compile("BANDWIDTH=(\\d+)\\b");
  private static final Pattern REGEX_CODECS = Pattern.compile("CODECS=\"(.+?)\"");
  private static final Pattern REGEX_RESOLUTION = Pattern.compile("RESOLUTION=(\\d+x\\d+)");
  private static final Pattern REGEX_TARGET_DURATION = Pattern.compile(TAG_TARGET_DURATION
      + ":(\\d+)\\b");
  private static final Pattern REGEX_VERSION = Pattern.compile(TAG_VERSION + ":(\\d+)\\b");
  private static final Pattern REGEX_PLAYLIST_TYPE = Pattern.compile(TAG_PLAYLIST_TYPE
      + ":(.+)\\b");
  private static final Pattern REGEX_MEDIA_SEQUENCE = Pattern.compile(TAG_MEDIA_SEQUENCE
      + ":(\\d+)\\b");
  private static final Pattern REGEX_MEDIA_DURATION = Pattern.compile(TAG_MEDIA_DURATION
      + ":([\\d\\.]+)\\b");
  private static final Pattern REGEX_TIME_OFFSET = Pattern.compile("TIME-OFFSET=(-?[\\d\\.]+)\\b");
  private static final Pattern REGEX_BYTERANGE = Pattern.compile(TAG_BYTERANGE
      + ":(\\d+(?:@\\d+)?)\\b");
  private static final Pattern REGEX_ATTR_BYTERANGE =
      Pattern.compile("BYTERANGE=\"(\\d+(?:@\\d+)?)\\b\"");
  private static final Pattern REGEX_METHOD = Pattern.compile("METHOD=(" + METHOD_NONE + "|"
      + METHOD_AES128 + ")");
  private static final Pattern REGEX_URI = Pattern.compile("URI=\"(.+?)\"");
  private static final Pattern REGEX_IV = Pattern.compile("IV=([^,.*]+)");
  private static final Pattern REGEX_TYPE = Pattern.compile("TYPE=(" + TYPE_AUDIO + "|" + TYPE_VIDEO
      + "|" + TYPE_SUBTITLES + "|" + TYPE_CLOSED_CAPTIONS + ")");
  private static final Pattern REGEX_LANGUAGE = Pattern.compile("LANGUAGE=\"(.+?)\"");
  private static final Pattern REGEX_NAME = Pattern.compile("NAME=\"(.+?)\"");
  private static final Pattern REGEX_INSTREAM_ID =
      Pattern.compile("INSTREAM-ID=\"((?:CC|SERVICE)\\d+)\"");
  private static final Pattern REGEX_AUTOSELECT = compileBooleanAttrPattern("AUTOSELECT");
  private static final Pattern REGEX_DEFAULT = compileBooleanAttrPattern("DEFAULT");
  private static final Pattern REGEX_FORCED = compileBooleanAttrPattern("FORCED");

  @Override
  public HlsPlaylist parse(Uri uri, InputStream inputStream) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    Queue<String> extraLines = new LinkedList<>();
    String line;
    try {
      if (!checkPlaylistHeader(reader)) {
        throw new UnrecognizedInputFormatException("Input does not start with the #EXTM3U header.",
            uri);
      }
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          // Do nothing.
        } else if (line.startsWith(TAG_STREAM_INF)) {
          extraLines.add(line);
          return parseMasterPlaylist(new LineIterator(extraLines, reader), uri.toString());
        } else if (line.startsWith(TAG_TARGET_DURATION)
            || line.startsWith(TAG_MEDIA_SEQUENCE)
            || line.startsWith(TAG_MEDIA_DURATION)
            || line.startsWith(TAG_KEY)
            || line.startsWith(TAG_BYTERANGE)
            || line.equals(TAG_DISCONTINUITY)
            || line.equals(TAG_DISCONTINUITY_SEQUENCE)
            || line.equals(TAG_ENDLIST)) {
          extraLines.add(line);
          return parseMediaPlaylist(new LineIterator(extraLines, reader), uri.toString());
        } else {
          extraLines.add(line);
        }
      }
    } finally {
      Util.closeQuietly(reader);
    }
    throw new ParserException("Failed to parse the playlist, could not identify any tags.");
  }

  private static boolean checkPlaylistHeader(BufferedReader reader) throws IOException {
    int last = reader.read();
    if (last == 0xEF) {
      if (reader.read() != 0xBB || reader.read() != 0xBF) {
        return false;
      }
      // The playlist contains a Byte Order Mark, which gets discarded.
      last = reader.read();
    }
    last = skipIgnorableWhitespace(reader, true, last);
    int playlistHeaderLength = PLAYLIST_HEADER.length();
    for (int i = 0; i < playlistHeaderLength; i++) {
      if (last != PLAYLIST_HEADER.charAt(i)) {
        return false;
      }
      last = reader.read();
    }
    last = skipIgnorableWhitespace(reader, false, last);
    return Util.isLinebreak(last);
  }

  private static int skipIgnorableWhitespace(BufferedReader reader, boolean skipLinebreaks, int c)
      throws IOException {
    while (c != -1 && Character.isWhitespace(c) && (skipLinebreaks || !Util.isLinebreak(c))) {
      c = reader.read();
    }
    return c;
  }

  private static HlsMasterPlaylist parseMasterPlaylist(LineIterator iterator, String baseUri)
      throws IOException {
    ArrayList<HlsMasterPlaylist.HlsUrl> variants = new ArrayList<>();
    ArrayList<HlsMasterPlaylist.HlsUrl> audios = new ArrayList<>();
    ArrayList<HlsMasterPlaylist.HlsUrl> subtitles = new ArrayList<>();
    Format muxedAudioFormat = null;
    ArrayList<Format> muxedCaptionFormats = new ArrayList<>();

    String line;
    while (iterator.hasNext()) {
      line = iterator.next();
      if (line.startsWith(TAG_MEDIA)) {
        @C.SelectionFlags int selectionFlags = parseSelectionFlags(line);
        String uri = parseOptionalStringAttr(line, REGEX_URI);
        String id = parseStringAttr(line, REGEX_NAME);
        String language = parseOptionalStringAttr(line, REGEX_LANGUAGE);
        Format format;
        switch (parseStringAttr(line, REGEX_TYPE)) {
          case TYPE_AUDIO:
            format = Format.createAudioContainerFormat(id, MimeTypes.APPLICATION_M3U8, null, null,
                Format.NO_VALUE, Format.NO_VALUE, Format.NO_VALUE, null, selectionFlags, language);
            if (uri == null) {
              muxedAudioFormat = format;
            } else {
              audios.add(new HlsMasterPlaylist.HlsUrl(uri, format));
            }
            break;
          case TYPE_SUBTITLES:
            format = Format.createTextContainerFormat(id, MimeTypes.APPLICATION_M3U8,
                MimeTypes.TEXT_VTT, null, Format.NO_VALUE, selectionFlags, language);
            subtitles.add(new HlsMasterPlaylist.HlsUrl(uri, format));
            break;
          case TYPE_CLOSED_CAPTIONS:
            String instreamId = parseStringAttr(line, REGEX_INSTREAM_ID);
            String mimeType;
            int accessibilityChannel;
            if (instreamId.startsWith("CC")) {
              mimeType = MimeTypes.APPLICATION_CEA608;
              accessibilityChannel = Integer.parseInt(instreamId.substring(2));
            } else /* starts with SERVICE */ {
              mimeType = MimeTypes.APPLICATION_CEA708;
              accessibilityChannel = Integer.parseInt(instreamId.substring(7));
            }
            muxedCaptionFormats.add(Format.createTextContainerFormat(id, null, mimeType, null,
                Format.NO_VALUE, selectionFlags, language, accessibilityChannel));
            break;
          default:
            // Do nothing.
            break;
        }
      } else if (line.startsWith(TAG_STREAM_INF)) {
        int bitrate = parseIntAttr(line, REGEX_BANDWIDTH);
        String codecs = parseOptionalStringAttr(line, REGEX_CODECS);
        String resolutionString = parseOptionalStringAttr(line, REGEX_RESOLUTION);
        int width;
        int height;
        if (resolutionString != null) {
          String[] widthAndHeight = resolutionString.split("x");
          width = Integer.parseInt(widthAndHeight[0]);
          height = Integer.parseInt(widthAndHeight[1]);
          if (width <= 0 || height <= 0) {
            // Resolution string is invalid.
            width = Format.NO_VALUE;
            height = Format.NO_VALUE;
          }
        } else {
          width = Format.NO_VALUE;
          height = Format.NO_VALUE;
        }
        line = iterator.next();
        Format format = Format.createVideoContainerFormat(Integer.toString(variants.size()),
            MimeTypes.APPLICATION_M3U8, null, codecs, bitrate, width, height, Format.NO_VALUE, null,
            0);
        variants.add(new HlsMasterPlaylist.HlsUrl(line, format));
      }
    }
    return new HlsMasterPlaylist(baseUri, variants, audios, subtitles, muxedAudioFormat,
        muxedCaptionFormats);
  }

  @C.SelectionFlags
  private static int parseSelectionFlags(String line) {
    return (parseBooleanAttribute(line, REGEX_DEFAULT, false) ? C.SELECTION_FLAG_DEFAULT : 0)
        | (parseBooleanAttribute(line, REGEX_FORCED, false) ? C.SELECTION_FLAG_FORCED : 0)
        | (parseBooleanAttribute(line, REGEX_AUTOSELECT, false) ? C.SELECTION_FLAG_AUTOSELECT : 0);
  }

  private static HlsMediaPlaylist parseMediaPlaylist(LineIterator iterator, String baseUri)
      throws IOException {
    @HlsMediaPlaylist.PlaylistType int playlistType = HlsMediaPlaylist.PLAYLIST_TYPE_UNKNOWN;
    long startOffsetUs = C.TIME_UNSET;
    int mediaSequence = 0;
    int version = 1; // Default version == 1.
    long targetDurationUs = C.TIME_UNSET;
    boolean hasEndTag = false;
    Segment initializationSegment = null;
    List<Segment> segments = new ArrayList<>();

    long segmentDurationUs = 0;
    boolean hasDiscontinuitySequence = false;
    int playlistDiscontinuitySequence = 0;
    int relativeDiscontinuitySequence = 0;
    long playlistStartTimeUs = 0;
    long segmentStartTimeUs = 0;
    long segmentByteRangeOffset = 0;
    long segmentByteRangeLength = C.LENGTH_UNSET;
    int segmentMediaSequence = 0;

    boolean isEncrypted = false;
    String encryptionKeyUri = null;
    String encryptionIV = null;

    String line;
    while (iterator.hasNext()) {
      line = iterator.next();
      if (line.startsWith(TAG_PLAYLIST_TYPE)) {
        String playlistTypeString = parseStringAttr(line, REGEX_PLAYLIST_TYPE);
        if ("VOD".equals(playlistTypeString)) {
          playlistType = HlsMediaPlaylist.PLAYLIST_TYPE_VOD;
        } else if ("EVENT".equals(playlistTypeString)) {
          playlistType = HlsMediaPlaylist.PLAYLIST_TYPE_EVENT;
        } else {
          throw new ParserException("Illegal playlist type: " + playlistTypeString);
        }
      } else if (line.startsWith(TAG_START)) {
        startOffsetUs = (long) (parseDoubleAttr(line, REGEX_TIME_OFFSET) * C.MICROS_PER_SECOND);
      } else if (line.startsWith(TAG_INIT_SEGMENT)) {
        String uri = parseStringAttr(line, REGEX_URI);
        String byteRange = parseOptionalStringAttr(line, REGEX_ATTR_BYTERANGE);
        if (byteRange != null) {
          String[] splitByteRange = byteRange.split("@");
          segmentByteRangeLength = Long.parseLong(splitByteRange[0]);
          if (splitByteRange.length > 1) {
            segmentByteRangeOffset = Long.parseLong(splitByteRange[1]);
          }
        }
        initializationSegment = new Segment(uri, segmentByteRangeOffset, segmentByteRangeLength);
        segmentByteRangeOffset = 0;
        segmentByteRangeLength = C.LENGTH_UNSET;
      } else if (line.startsWith(TAG_TARGET_DURATION)) {
        targetDurationUs = parseIntAttr(line, REGEX_TARGET_DURATION) * C.MICROS_PER_SECOND;
      } else if (line.startsWith(TAG_MEDIA_SEQUENCE)) {
        mediaSequence = parseIntAttr(line, REGEX_MEDIA_SEQUENCE);
        segmentMediaSequence = mediaSequence;
      } else if (line.startsWith(TAG_VERSION)) {
        version = parseIntAttr(line, REGEX_VERSION);
      } else if (line.startsWith(TAG_MEDIA_DURATION)) {
        segmentDurationUs =
            (long) (parseDoubleAttr(line, REGEX_MEDIA_DURATION) * C.MICROS_PER_SECOND);
      } else if (line.startsWith(TAG_KEY)) {
        String method = parseStringAttr(line, REGEX_METHOD);
        isEncrypted = METHOD_AES128.equals(method);
        if (isEncrypted) {
          encryptionKeyUri = parseStringAttr(line, REGEX_URI);
          encryptionIV = parseOptionalStringAttr(line, REGEX_IV);
        } else {
          encryptionKeyUri = null;
          encryptionIV = null;
        }
      } else if (line.startsWith(TAG_BYTERANGE)) {
        String byteRange = parseStringAttr(line, REGEX_BYTERANGE);
        String[] splitByteRange = byteRange.split("@");
        segmentByteRangeLength = Long.parseLong(splitByteRange[0]);
        if (splitByteRange.length > 1) {
          segmentByteRangeOffset = Long.parseLong(splitByteRange[1]);
        }
      } else if (line.startsWith(TAG_DISCONTINUITY_SEQUENCE)) {
        hasDiscontinuitySequence = true;
        playlistDiscontinuitySequence = Integer.parseInt(line.substring(line.indexOf(':') + 1));
      } else if (line.equals(TAG_DISCONTINUITY)) {
        relativeDiscontinuitySequence++;
      } else if (line.startsWith(TAG_PROGRAM_DATE_TIME)) {
        if (playlistStartTimeUs == 0) {
          long programDatetimeUs =
              C.msToUs(Util.parseXsDateTime(line.substring(line.indexOf(':') + 1)));
          playlistStartTimeUs = programDatetimeUs - segmentStartTimeUs;
        }
      } else if (!line.startsWith("#")) {
        String segmentEncryptionIV;
        if (!isEncrypted) {
          segmentEncryptionIV = null;
        } else if (encryptionIV != null) {
          segmentEncryptionIV = encryptionIV;
        } else {
          segmentEncryptionIV = Integer.toHexString(segmentMediaSequence);
        }
        segmentMediaSequence++;
        if (segmentByteRangeLength == C.LENGTH_UNSET) {
          segmentByteRangeOffset = 0;
        }
        segments.add(new Segment(line, segmentDurationUs, relativeDiscontinuitySequence,
            segmentStartTimeUs, isEncrypted, encryptionKeyUri, segmentEncryptionIV,
            segmentByteRangeOffset, segmentByteRangeLength));
        segmentStartTimeUs += segmentDurationUs;
        segmentDurationUs = 0;
        if (segmentByteRangeLength != C.LENGTH_UNSET) {
          segmentByteRangeOffset += segmentByteRangeLength;
        }
        segmentByteRangeLength = C.LENGTH_UNSET;
      } else if (line.equals(TAG_ENDLIST)) {
        hasEndTag = true;
      }
    }
    return new HlsMediaPlaylist(playlistType, baseUri, startOffsetUs, playlistStartTimeUs,
        hasDiscontinuitySequence, playlistDiscontinuitySequence, mediaSequence, version,
        targetDurationUs, hasEndTag, playlistStartTimeUs != 0, initializationSegment, segments);
  }

  private static String parseStringAttr(String line, Pattern pattern) throws ParserException {
    Matcher matcher = pattern.matcher(line);
    if (matcher.find() && matcher.groupCount() == 1) {
      return matcher.group(1);
    }
    throw new ParserException("Couldn't match " + pattern.pattern() + " in " + line);
  }

  private static int parseIntAttr(String line, Pattern pattern) throws ParserException {
    return Integer.parseInt(parseStringAttr(line, pattern));
  }

  private static double parseDoubleAttr(String line, Pattern pattern) throws ParserException {
    return Double.parseDouble(parseStringAttr(line, pattern));
  }

  private static String parseOptionalStringAttr(String line, Pattern pattern) {
    Matcher matcher = pattern.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private static boolean parseBooleanAttribute(String line, Pattern pattern, boolean defaultValue) {
    Matcher matcher = pattern.matcher(line);
    if (matcher.find()) {
      return matcher.group(1).equals(BOOLEAN_TRUE);
    }
    return defaultValue;
  }

  private static Pattern compileBooleanAttrPattern(String attribute) {
    return Pattern.compile(attribute + "=(" + BOOLEAN_FALSE + "|" + BOOLEAN_TRUE + ")");
  }

  private static class LineIterator {

    private final BufferedReader reader;
    private final Queue<String> extraLines;

    private String next;

    public LineIterator(Queue<String> extraLines, BufferedReader reader) {
      this.extraLines = extraLines;
      this.reader = reader;
    }

    public boolean hasNext() throws IOException {
      if (next != null) {
        return true;
      }
      if (!extraLines.isEmpty()) {
        next = extraLines.poll();
        return true;
      }
      while ((next = reader.readLine()) != null) {
        next = next.trim();
        if (!next.isEmpty()) {
          return true;
        }
      }
      return false;
    }

    public String next() throws IOException {
      String result = null;
      if (hasNext()) {
        result = next;
        next = null;
      }
      return result;
    }

  }

}

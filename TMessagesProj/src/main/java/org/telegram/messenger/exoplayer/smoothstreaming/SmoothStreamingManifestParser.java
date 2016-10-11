/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.smoothstreaming;

import android.util.Base64;
import android.util.Pair;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.extractor.mp4.PsshAtomUtil;
import org.telegram.messenger.exoplayer.smoothstreaming.SmoothStreamingManifest.ProtectionElement;
import org.telegram.messenger.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import org.telegram.messenger.exoplayer.smoothstreaming.SmoothStreamingManifest.TrackElement;
import org.telegram.messenger.exoplayer.upstream.UriLoadable;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.CodecSpecificDataUtil;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Parses SmoothStreaming client manifests.
 *
 * @see <a href="http://msdn.microsoft.com/en-us/library/ee673436(v=vs.90).aspx">
 * IIS Smooth Streaming Client Manifest Format</a>
 */
public class SmoothStreamingManifestParser implements UriLoadable.Parser<SmoothStreamingManifest> {

  private final XmlPullParserFactory xmlParserFactory;

  public SmoothStreamingManifestParser() {
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  @Override
  public SmoothStreamingManifest parse(String connectionUrl, InputStream inputStream)
      throws IOException, ParserException {
    try {
      XmlPullParser xmlParser = xmlParserFactory.newPullParser();
      xmlParser.setInput(inputStream, null);
      SmoothStreamMediaParser smoothStreamMediaParser =
          new SmoothStreamMediaParser(null, connectionUrl);
      return (SmoothStreamingManifest) smoothStreamMediaParser.parse(xmlParser);
    } catch (XmlPullParserException e) {
      throw new ParserException(e);
    }
  }

  /**
   * Thrown if a required field is missing.
   */
  public static class MissingFieldException extends ParserException {

    public MissingFieldException(String fieldName) {
      super("Missing required field: " + fieldName);
    }

  }

  /**
   * A base class for parsers that parse components of a smooth streaming manifest.
   */
  private static abstract class ElementParser {

    private final String baseUri;
    private final String tag;

    private final ElementParser parent;
    private final List<Pair<String, Object>> normalizedAttributes;

    public ElementParser(ElementParser parent, String baseUri, String tag) {
      this.parent = parent;
      this.baseUri = baseUri;
      this.tag = tag;
      this.normalizedAttributes = new LinkedList<>();
    }

    public final Object parse(XmlPullParser xmlParser) throws XmlPullParserException, IOException,
        ParserException {
      String tagName;
      boolean foundStartTag = false;
      int skippingElementDepth = 0;
      while (true) {
        int eventType = xmlParser.getEventType();
        switch (eventType) {
          case XmlPullParser.START_TAG:
            tagName = xmlParser.getName();
            if (tag.equals(tagName)) {
              foundStartTag = true;
              parseStartTag(xmlParser);
            } else if (foundStartTag) {
              if (skippingElementDepth > 0) {
                skippingElementDepth++;
              } else if (handleChildInline(tagName)) {
                parseStartTag(xmlParser);
              } else {
                ElementParser childElementParser = newChildParser(this, tagName, baseUri);
                if (childElementParser == null) {
                  skippingElementDepth = 1;
                } else {
                  addChild(childElementParser.parse(xmlParser));
                }
              }
            }
            break;
          case XmlPullParser.TEXT:
            if (foundStartTag && skippingElementDepth == 0) {
              parseText(xmlParser);
            }
            break;
          case XmlPullParser.END_TAG:
            if (foundStartTag) {
              if (skippingElementDepth > 0) {
                skippingElementDepth--;
              } else {
                tagName = xmlParser.getName();
                parseEndTag(xmlParser);
                if (!handleChildInline(tagName)) {
                  return build();
                }
              }
            }
            break;
          case XmlPullParser.END_DOCUMENT:
            return null;
          default:
            // Do nothing.
            break;
        }
        xmlParser.next();
      }
    }

    private ElementParser newChildParser(ElementParser parent, String name, String baseUri) {
      if (TrackElementParser.TAG.equals(name)) {
        return new TrackElementParser(parent, baseUri);
      } else if (ProtectionElementParser.TAG.equals(name)) {
        return new ProtectionElementParser(parent, baseUri);
      } else if (StreamElementParser.TAG.equals(name)) {
        return new StreamElementParser(parent, baseUri);
      }
      return null;
    }

    /**
     * Stash an attribute that may be normalized at this level. In other words, an attribute that
     * may have been pulled up from the child elements because its value was the same in all
     * children.
     * <p>
     * Stashing an attribute allows child element parsers to retrieve the values of normalized
     * attributes using {@link #getNormalizedAttribute(String)}.
     *
     * @param key The name of the attribute.
     * @param value The value of the attribute.
     */
    protected final void putNormalizedAttribute(String key, Object value) {
      normalizedAttributes.add(Pair.create(key, value));
    }

    /**
     * Attempt to retrieve a stashed normalized attribute. If there is no stashed attribute with
     * the provided name, the parent element parser will be queried, and so on up the chain.
     *
     * @param key The name of the attribute.
     * @return The stashed value, or null if the attribute was not be found.
     */
    protected final Object getNormalizedAttribute(String key) {
      for (int i = 0; i < normalizedAttributes.size(); i++) {
        Pair<String, Object> pair = normalizedAttributes.get(i);
        if (pair.first.equals(key)) {
          return pair.second;
        }
      }
      return parent == null ? null : parent.getNormalizedAttribute(key);
    }

    /**
     * Whether this {@link ElementParser} parses a child element inline.
     *
     * @param tagName The name of the child element.
     * @return Whether the child is parsed inline.
     */
    protected boolean handleChildInline(String tagName) {
      return false;
    }

    /**
     * @param xmlParser The underlying {@link XmlPullParser}
     * @throws ParserException
     */
    protected void parseStartTag(XmlPullParser xmlParser) throws ParserException {
      // Do nothing.
    }

    /**
     * @param xmlParser The underlying {@link XmlPullParser}
     * @throws ParserException
     */
    protected void parseText(XmlPullParser xmlParser) throws ParserException {
      // Do nothing.
    }

    /**
     * @param xmlParser The underlying {@link XmlPullParser}
     * @throws ParserException
     */
    protected void parseEndTag(XmlPullParser xmlParser) throws ParserException {
      // Do nothing.
    }

    /**
     * @param parsedChild A parsed child object.
     */
    protected void addChild(Object parsedChild) {
      // Do nothing.
    }

    protected abstract Object build();

    protected final String parseRequiredString(XmlPullParser parser, String key)
        throws MissingFieldException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        return value;
      } else {
        throw new MissingFieldException(key);
      }
    }

    protected final int parseInt(XmlPullParser parser, String key, int defaultValue)
        throws ParserException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        try {
          return Integer.parseInt(value);
        } catch (NumberFormatException e) {
          throw new ParserException(e);
        }
      } else {
        return defaultValue;
      }
    }

    protected final int parseRequiredInt(XmlPullParser parser, String key) throws ParserException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        try {
          return Integer.parseInt(value);
        } catch (NumberFormatException e) {
          throw new ParserException(e);
        }
      } else {
        throw new MissingFieldException(key);
      }
    }

    protected final long parseLong(XmlPullParser parser, String key, long defaultValue)
        throws ParserException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        try {
          return Long.parseLong(value);
        } catch (NumberFormatException e) {
          throw new ParserException(e);
        }
      } else {
        return defaultValue;
      }
    }

    protected final long parseRequiredLong(XmlPullParser parser, String key)
        throws ParserException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        try {
          return Long.parseLong(value);
        } catch (NumberFormatException e) {
          throw new ParserException(e);
        }
      } else {
        throw new MissingFieldException(key);
      }
    }

    protected final boolean parseBoolean(XmlPullParser parser, String key, boolean defaultValue) {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        return Boolean.parseBoolean(value);
      } else {
        return defaultValue;
      }
    }

  }

  private static class SmoothStreamMediaParser extends ElementParser {

    public static final String TAG = "SmoothStreamingMedia";

    private static final String KEY_MAJOR_VERSION = "MajorVersion";
    private static final String KEY_MINOR_VERSION = "MinorVersion";
    private static final String KEY_TIME_SCALE = "TimeScale";
    private static final String KEY_DVR_WINDOW_LENGTH = "DVRWindowLength";
    private static final String KEY_DURATION = "Duration";
    private static final String KEY_LOOKAHEAD_COUNT = "LookaheadCount";
    private static final String KEY_IS_LIVE = "IsLive";

    private int majorVersion;
    private int minorVersion;
    private long timescale;
    private long duration;
    private long dvrWindowLength;
    private int lookAheadCount;
    private boolean isLive;
    private ProtectionElement protectionElement;
    private List<StreamElement> streamElements;

    public SmoothStreamMediaParser(ElementParser parent, String baseUri) {
      super(parent, baseUri, TAG);
      lookAheadCount = -1;
      protectionElement = null;
      streamElements = new LinkedList<>();
    }

    @Override
    public void parseStartTag(XmlPullParser parser) throws ParserException {
      majorVersion = parseRequiredInt(parser, KEY_MAJOR_VERSION);
      minorVersion = parseRequiredInt(parser, KEY_MINOR_VERSION);
      timescale = parseLong(parser, KEY_TIME_SCALE, 10000000L);
      duration = parseRequiredLong(parser, KEY_DURATION);
      dvrWindowLength = parseLong(parser, KEY_DVR_WINDOW_LENGTH, 0);
      lookAheadCount = parseInt(parser, KEY_LOOKAHEAD_COUNT, -1);
      isLive = parseBoolean(parser, KEY_IS_LIVE, false);
      putNormalizedAttribute(KEY_TIME_SCALE, timescale);
    }

    @Override
    public void addChild(Object child) {
      if (child instanceof StreamElement) {
        streamElements.add((StreamElement) child);
      } else if (child instanceof ProtectionElement) {
        Assertions.checkState(protectionElement == null);
        protectionElement = (ProtectionElement) child;
      }
    }

    @Override
    public Object build() {
      StreamElement[] streamElementArray = new StreamElement[streamElements.size()];
      streamElements.toArray(streamElementArray);
      return new SmoothStreamingManifest(majorVersion, minorVersion, timescale, duration,
          dvrWindowLength, lookAheadCount, isLive, protectionElement, streamElementArray);
    }

  }

  private static class ProtectionElementParser extends ElementParser {

    public static final String TAG = "Protection";
    public static final String TAG_PROTECTION_HEADER = "ProtectionHeader";

    public static final String KEY_SYSTEM_ID = "SystemID";

    private boolean inProtectionHeader;
    private UUID uuid;
    private byte[] initData;

    public ProtectionElementParser(ElementParser parent, String baseUri) {
      super(parent, baseUri, TAG);
    }

    @Override
    public boolean handleChildInline(String tag) {
      return TAG_PROTECTION_HEADER.equals(tag);
    }

    @Override
    public void parseStartTag(XmlPullParser parser) {
      if (TAG_PROTECTION_HEADER.equals(parser.getName())) {
        inProtectionHeader = true;
        String uuidString = parser.getAttributeValue(null, KEY_SYSTEM_ID);
        uuidString = stripCurlyBraces(uuidString);
        uuid = UUID.fromString(uuidString);
      }
    }

    @Override
    public void parseText(XmlPullParser parser) {
      if (inProtectionHeader) {
        initData = Base64.decode(parser.getText(), Base64.DEFAULT);
      }
    }

    @Override
    public void parseEndTag(XmlPullParser parser) {
      if (TAG_PROTECTION_HEADER.equals(parser.getName())) {
        inProtectionHeader = false;
      }
    }

    @Override
    public Object build() {
      return new ProtectionElement(uuid, PsshAtomUtil.buildPsshAtom(uuid, initData));
    }

    private static String stripCurlyBraces(String uuidString) {
      if (uuidString.charAt(0) == '{' && uuidString.charAt(uuidString.length() - 1) == '}') {
        uuidString = uuidString.substring(1, uuidString.length() - 1);
      }
      return uuidString;
    }
  }

  private static class StreamElementParser extends ElementParser {

    public static final String TAG = "StreamIndex";
    private static final String TAG_STREAM_FRAGMENT = "c";

    private static final String KEY_TYPE = "Type";
    private static final String KEY_TYPE_AUDIO = "audio";
    private static final String KEY_TYPE_VIDEO = "video";
    private static final String KEY_TYPE_TEXT = "text";
    private static final String KEY_SUB_TYPE = "Subtype";
    private static final String KEY_NAME = "Name";
    private static final String KEY_QUALITY_LEVELS = "QualityLevels";
    private static final String KEY_URL = "Url";
    private static final String KEY_MAX_WIDTH = "MaxWidth";
    private static final String KEY_MAX_HEIGHT = "MaxHeight";
    private static final String KEY_DISPLAY_WIDTH = "DisplayWidth";
    private static final String KEY_DISPLAY_HEIGHT = "DisplayHeight";
    private static final String KEY_LANGUAGE = "Language";
    private static final String KEY_TIME_SCALE = "TimeScale";

    private static final String KEY_FRAGMENT_DURATION = "d";
    private static final String KEY_FRAGMENT_START_TIME = "t";
    private static final String KEY_FRAGMENT_REPEAT_COUNT = "r";

    private final String baseUri;
    private final List<TrackElement> tracks;

    private int type;
    private String subType;
    private long timescale;
    private String name;
    private int qualityLevels;
    private String url;
    private int maxWidth;
    private int maxHeight;
    private int displayWidth;
    private int displayHeight;
    private String language;
    private ArrayList<Long> startTimes;

    private long lastChunkDuration;

    public StreamElementParser(ElementParser parent, String baseUri) {
      super(parent, baseUri, TAG);
      this.baseUri = baseUri;
      tracks = new LinkedList<>();
    }

    @Override
    public boolean handleChildInline(String tag) {
      return TAG_STREAM_FRAGMENT.equals(tag);
    }

    @Override
    public void parseStartTag(XmlPullParser parser) throws ParserException {
      if (TAG_STREAM_FRAGMENT.equals(parser.getName())) {
        parseStreamFragmentStartTag(parser);
      } else {
        parseStreamElementStartTag(parser);
      }
    }

    private void parseStreamFragmentStartTag(XmlPullParser parser) throws ParserException {
      int chunkIndex = startTimes.size();
      long startTime = parseLong(parser, KEY_FRAGMENT_START_TIME, -1L);
      if (startTime == -1L) {
        if (chunkIndex == 0) {
          // Assume the track starts at t = 0.
          startTime = 0;
        } else if (lastChunkDuration != -1L) {
          // Infer the start time from the previous chunk's start time and duration.
          startTime = startTimes.get(chunkIndex - 1) + lastChunkDuration;
        } else {
          // We don't have the start time, and we're unable to infer it.
          throw new ParserException("Unable to infer start time");
        }
      }
      chunkIndex++;
      startTimes.add(startTime);
      lastChunkDuration = parseLong(parser, KEY_FRAGMENT_DURATION, -1L);
      // Handle repeated chunks.
      long repeatCount = parseLong(parser, KEY_FRAGMENT_REPEAT_COUNT, 1L);
      if (repeatCount > 1 && lastChunkDuration == -1L) {
        throw new ParserException("Repeated chunk with unspecified duration");
      }
      for (int i = 1; i < repeatCount; i++) {
        chunkIndex++;
        startTimes.add(startTime + (lastChunkDuration * i));
      }
    }

    private void parseStreamElementStartTag(XmlPullParser parser) throws ParserException {
      type = parseType(parser);
      putNormalizedAttribute(KEY_TYPE, type);
      if (type == StreamElement.TYPE_TEXT) {
        subType = parseRequiredString(parser, KEY_SUB_TYPE);
      } else {
        subType = parser.getAttributeValue(null, KEY_SUB_TYPE);
      }
      name = parser.getAttributeValue(null, KEY_NAME);
      qualityLevels = parseInt(parser, KEY_QUALITY_LEVELS, -1);
      url = parseRequiredString(parser, KEY_URL);
      maxWidth = parseInt(parser, KEY_MAX_WIDTH, -1);
      maxHeight = parseInt(parser, KEY_MAX_HEIGHT, -1);
      displayWidth = parseInt(parser, KEY_DISPLAY_WIDTH, -1);
      displayHeight = parseInt(parser, KEY_DISPLAY_HEIGHT, -1);
      language = parser.getAttributeValue(null, KEY_LANGUAGE);
      putNormalizedAttribute(KEY_LANGUAGE, language);
      timescale = parseInt(parser, KEY_TIME_SCALE, -1);
      if (timescale == -1) {
        timescale = (Long) getNormalizedAttribute(KEY_TIME_SCALE);
      }
      startTimes = new ArrayList<>();
    }

    private int parseType(XmlPullParser parser) throws ParserException {
      String value = parser.getAttributeValue(null, KEY_TYPE);
      if (value != null) {
        if (KEY_TYPE_AUDIO.equalsIgnoreCase(value)) {
          return StreamElement.TYPE_AUDIO;
        } else if (KEY_TYPE_VIDEO.equalsIgnoreCase(value)) {
          return StreamElement.TYPE_VIDEO;
        } else if (KEY_TYPE_TEXT.equalsIgnoreCase(value)) {
          return StreamElement.TYPE_TEXT;
        } else {
          throw new ParserException("Invalid key value[" + value + "]");
        }
      }
      throw new MissingFieldException(KEY_TYPE);
    }

    @Override
    public void addChild(Object child) {
      if (child instanceof TrackElement) {
        tracks.add((TrackElement) child);
      }
    }

    @Override
    public Object build() {
      TrackElement[] trackElements = new TrackElement[tracks.size()];
      tracks.toArray(trackElements);
      return new StreamElement(baseUri, url, type, subType, timescale, name, qualityLevels,
          maxWidth, maxHeight, displayWidth, displayHeight, language, trackElements, startTimes,
          lastChunkDuration);
    }

  }

  private static class TrackElementParser extends ElementParser {

    public static final String TAG = "QualityLevel";

    private static final String KEY_INDEX = "Index";
    private static final String KEY_BITRATE = "Bitrate";
    private static final String KEY_CODEC_PRIVATE_DATA = "CodecPrivateData";
    private static final String KEY_SAMPLING_RATE = "SamplingRate";
    private static final String KEY_CHANNELS = "Channels";
    private static final String KEY_FOUR_CC = "FourCC";
    private static final String KEY_TYPE = "Type";
    private static final String KEY_LANGUAGE = "Language";
    private static final String KEY_MAX_WIDTH = "MaxWidth";
    private static final String KEY_MAX_HEIGHT = "MaxHeight";

    private final List<byte[]> csd;

    private int index;
    private int bitrate;
    private String mimeType;
    private int maxWidth;
    private int maxHeight;
    private int samplingRate;
    private int channels;
    private String language;

    public TrackElementParser(ElementParser parent, String baseUri) {
      super(parent, baseUri, TAG);
      this.csd = new LinkedList<>();
    }

    @Override
    public void parseStartTag(XmlPullParser parser) throws ParserException {
      int type = (Integer) getNormalizedAttribute(KEY_TYPE);
      String value;

      index = parseInt(parser, KEY_INDEX, -1);
      bitrate = parseRequiredInt(parser, KEY_BITRATE);
      language = (String) getNormalizedAttribute(KEY_LANGUAGE);

      if (type == StreamElement.TYPE_VIDEO) {
        maxHeight = parseRequiredInt(parser, KEY_MAX_HEIGHT);
        maxWidth = parseRequiredInt(parser, KEY_MAX_WIDTH);
        mimeType = fourCCToMimeType(parseRequiredString(parser, KEY_FOUR_CC));
      } else {
        maxHeight = -1;
        maxWidth = -1;
        String fourCC = parser.getAttributeValue(null, KEY_FOUR_CC);
        // If fourCC is missing and the stream type is audio, we assume AAC.
        mimeType = fourCC != null ? fourCCToMimeType(fourCC)
            : type == StreamElement.TYPE_AUDIO ? MimeTypes.AUDIO_AAC : null;
      }

      if (type == StreamElement.TYPE_AUDIO) {
        samplingRate = parseRequiredInt(parser, KEY_SAMPLING_RATE);
        channels = parseRequiredInt(parser, KEY_CHANNELS);
      } else {
        samplingRate = -1;
        channels = -1;
      }

      value = parser.getAttributeValue(null, KEY_CODEC_PRIVATE_DATA);
      if (value != null && value.length() > 0) {
        byte[] codecPrivateData = Util.getBytesFromHexString(value);
        byte[][] split = CodecSpecificDataUtil.splitNalUnits(codecPrivateData);
        if (split == null) {
          csd.add(codecPrivateData);
        } else {
          for (int i = 0; i < split.length; i++) {
            csd.add(split[i]);
          }
        }
      }
    }

    @Override
    public Object build() {
      byte[][] csdArray = null;
      if (!csd.isEmpty()) {
        csdArray = new byte[csd.size()][];
        csd.toArray(csdArray);
      }
      return new TrackElement(index, bitrate, mimeType, csdArray, maxWidth, maxHeight, samplingRate,
          channels, language);
    }

    private static String fourCCToMimeType(String fourCC) {
      if (fourCC.equalsIgnoreCase("H264") || fourCC.equalsIgnoreCase("X264")
          || fourCC.equalsIgnoreCase("AVC1") || fourCC.equalsIgnoreCase("DAVC")) {
        return MimeTypes.VIDEO_H264;
      } else if (fourCC.equalsIgnoreCase("AAC") || fourCC.equalsIgnoreCase("AACL")
          || fourCC.equalsIgnoreCase("AACH") || fourCC.equalsIgnoreCase("AACP")) {
        return MimeTypes.AUDIO_AAC;
      } else if (fourCC.equalsIgnoreCase("TTML")) {
        return MimeTypes.APPLICATION_TTML;
      } else if (fourCC.equalsIgnoreCase("ac-3") || fourCC.equalsIgnoreCase("dac3")) {
        return MimeTypes.AUDIO_AC3;
      } else if (fourCC.equalsIgnoreCase("ec-3") || fourCC.equalsIgnoreCase("dec3")) {
        return MimeTypes.AUDIO_E_AC3;
      } else if (fourCC.equalsIgnoreCase("dtsc")) {
        return MimeTypes.AUDIO_DTS;
      } else if (fourCC.equalsIgnoreCase("dtsh") || fourCC.equalsIgnoreCase("dtsl")) {
        return MimeTypes.AUDIO_DTS_HD;
      } else if (fourCC.equalsIgnoreCase("dtse")) {
        return MimeTypes.AUDIO_DTS_EXPRESS;
      } else if (fourCC.equalsIgnoreCase("opus")) {
        return MimeTypes.AUDIO_OPUS;
      }
      return null;
    }

  }

}

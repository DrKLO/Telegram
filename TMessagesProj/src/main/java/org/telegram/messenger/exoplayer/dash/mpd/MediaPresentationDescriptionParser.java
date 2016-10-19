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
package org.telegram.messenger.exoplayer.dash.mpd;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.chunk.Format;
import org.telegram.messenger.exoplayer.dash.mpd.SegmentBase.SegmentList;
import org.telegram.messenger.exoplayer.dash.mpd.SegmentBase.SegmentTemplate;
import org.telegram.messenger.exoplayer.dash.mpd.SegmentBase.SegmentTimelineElement;
import org.telegram.messenger.exoplayer.dash.mpd.SegmentBase.SingleSegmentBase;
import org.telegram.messenger.exoplayer.drm.DrmInitData.SchemeInitData;
import org.telegram.messenger.exoplayer.extractor.mp4.PsshAtomUtil;
import org.telegram.messenger.exoplayer.upstream.UriLoadable;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.ParserUtil;
import org.telegram.messenger.exoplayer.util.UriUtil;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * A parser of media presentation description files.
 */
public class MediaPresentationDescriptionParser extends DefaultHandler
    implements UriLoadable.Parser<MediaPresentationDescription> {

  private static final String TAG = "MediaPresentationDescriptionParser";

  private static final Pattern FRAME_RATE_PATTERN = Pattern.compile("(\\d+)(?:/(\\d+))?");

  private final String contentId;
  private final XmlPullParserFactory xmlParserFactory;

  /**
   * Equivalent to calling {@code new MediaPresentationDescriptionParser(null)}.
   */
  public MediaPresentationDescriptionParser() {
    this(null);
  }

  /**
   * @param contentId An optional content identifier to include in the parsed manifest.
   */
  // TODO: Remove the need to inject a content identifier here, by not including it in the parsed
  // manifest. Instead, it should be injected directly where needed (i.e. DashChunkSource).
  public MediaPresentationDescriptionParser(String contentId) {
    this.contentId = contentId;
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  // MPD parsing.

  @Override
  public MediaPresentationDescription parse(String connectionUrl, InputStream inputStream)
      throws IOException, ParserException {
    try {
      XmlPullParser xpp = xmlParserFactory.newPullParser();
      xpp.setInput(inputStream, null);
      int eventType = xpp.next();
      if (eventType != XmlPullParser.START_TAG || !"MPD".equals(xpp.getName())) {
        throw new ParserException(
            "inputStream does not contain a valid media presentation description");
      }
      return parseMediaPresentationDescription(xpp, connectionUrl);
    } catch (XmlPullParserException e) {
      throw new ParserException(e);
    } catch (ParseException e) {
      throw new ParserException(e);
    }
  }

  protected MediaPresentationDescription parseMediaPresentationDescription(XmlPullParser xpp,
      String baseUrl) throws XmlPullParserException, IOException, ParseException {
    long availabilityStartTime = parseDateTime(xpp, "availabilityStartTime", -1);
    long durationMs = parseDuration(xpp, "mediaPresentationDuration", -1);
    long minBufferTimeMs = parseDuration(xpp, "minBufferTime", -1);
    String typeString = xpp.getAttributeValue(null, "type");
    boolean dynamic = (typeString != null) ? typeString.equals("dynamic") : false;
    long minUpdateTimeMs = (dynamic) ? parseDuration(xpp, "minimumUpdatePeriod", -1) : -1;
    long timeShiftBufferDepthMs = (dynamic) ? parseDuration(xpp, "timeShiftBufferDepth", -1) : -1;
    UtcTimingElement utcTiming = null;
    String location = null;

    List<Period> periods = new ArrayList<>();
    long nextPeriodStartMs = dynamic ? -1 : 0;
    boolean seenEarlyAccessPeriod = false;
    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (ParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrl = parseBaseUrl(xpp, baseUrl);
          seenFirstBaseUrl = true;
        }
      } else if (ParserUtil.isStartTag(xpp, "UTCTiming")) {
        utcTiming = parseUtcTiming(xpp);
      } else if (ParserUtil.isStartTag(xpp, "Location")) {
        location = xpp.nextText();
      } else if (ParserUtil.isStartTag(xpp, "Period") && !seenEarlyAccessPeriod) {
        Pair<Period, Long> periodWithDurationMs = parsePeriod(xpp, baseUrl, nextPeriodStartMs);
        Period period = periodWithDurationMs.first;
        if (period.startMs == -1) {
          if (dynamic) {
            // This is an early access period. Ignore it. All subsequent periods must also be
            // early access.
            seenEarlyAccessPeriod = true;
          } else {
            throw new ParserException("Unable to determine start of period " + periods.size());
          }
        } else {
          long periodDurationMs = periodWithDurationMs.second;
          nextPeriodStartMs = periodDurationMs == -1 ? -1 : period.startMs + periodDurationMs;
          periods.add(period);
        }
      }
    } while (!ParserUtil.isEndTag(xpp, "MPD"));

    if (durationMs == -1) {
      if (nextPeriodStartMs != -1) {
        // If we know the end time of the final period, we can use it as the duration.
        durationMs = nextPeriodStartMs;
      } else if (!dynamic) {
        throw new ParserException("Unable to determine duration of static manifest.");
      }
    }

    if (periods.isEmpty()) {
      throw new ParserException("No periods found.");
    }

    return buildMediaPresentationDescription(availabilityStartTime, durationMs, minBufferTimeMs,
        dynamic, minUpdateTimeMs, timeShiftBufferDepthMs, utcTiming, location, periods);
  }

  protected MediaPresentationDescription buildMediaPresentationDescription(
      long availabilityStartTime, long durationMs, long minBufferTimeMs, boolean dynamic,
      long minUpdateTimeMs, long timeShiftBufferDepthMs, UtcTimingElement utcTiming,
      String location, List<Period> periods) {
    return new MediaPresentationDescription(availabilityStartTime, durationMs, minBufferTimeMs,
        dynamic, minUpdateTimeMs, timeShiftBufferDepthMs, utcTiming, location, periods);
  }

  protected UtcTimingElement parseUtcTiming(XmlPullParser xpp) {
    String schemeIdUri = xpp.getAttributeValue(null, "schemeIdUri");
    String value = xpp.getAttributeValue(null, "value");
    return buildUtcTimingElement(schemeIdUri, value);
  }

  protected UtcTimingElement buildUtcTimingElement(String schemeIdUri, String value) {
    return new UtcTimingElement(schemeIdUri, value);
  }

  protected Pair<Period, Long> parsePeriod(XmlPullParser xpp, String baseUrl, long defaultStartMs)
      throws XmlPullParserException, IOException {
    String id = xpp.getAttributeValue(null, "id");
    long startMs = parseDuration(xpp, "start", defaultStartMs);
    long durationMs = parseDuration(xpp, "duration", -1);
    SegmentBase segmentBase = null;
    List<AdaptationSet> adaptationSets = new ArrayList<>();
    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (ParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrl = parseBaseUrl(xpp, baseUrl);
          seenFirstBaseUrl = true;
        }
      } else if (ParserUtil.isStartTag(xpp, "AdaptationSet")) {
        adaptationSets.add(parseAdaptationSet(xpp, baseUrl, segmentBase));
      } else if (ParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, baseUrl, null);
      } else if (ParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, baseUrl, null);
      } else if (ParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, baseUrl, null);
      }
    } while (!ParserUtil.isEndTag(xpp, "Period"));

    return Pair.create(buildPeriod(id, startMs, adaptationSets), durationMs);
  }

  protected Period buildPeriod(String id, long startMs, List<AdaptationSet> adaptationSets) {
    return new Period(id, startMs, adaptationSets);
  }

  // AdaptationSet parsing.

  protected AdaptationSet parseAdaptationSet(XmlPullParser xpp, String baseUrl,
      SegmentBase segmentBase) throws XmlPullParserException, IOException {
    int id = parseInt(xpp, "id", -1);
    int contentType = parseContentType(xpp);

    String mimeType = xpp.getAttributeValue(null, "mimeType");
    String codecs = xpp.getAttributeValue(null, "codecs");
    int width = parseInt(xpp, "width", -1);
    int height = parseInt(xpp, "height", -1);
    float frameRate = parseFrameRate(xpp, -1);
    int audioChannels = -1;
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate", -1);
    String language = xpp.getAttributeValue(null, "lang");

    ContentProtectionsBuilder contentProtectionsBuilder = new ContentProtectionsBuilder();
    List<Representation> representations = new ArrayList<>();
    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (ParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrl = parseBaseUrl(xpp, baseUrl);
          seenFirstBaseUrl = true;
        }
      } else if (ParserUtil.isStartTag(xpp, "ContentProtection")) {
        ContentProtection contentProtection = parseContentProtection(xpp);
        if (contentProtection != null) {
          contentProtectionsBuilder.addAdaptationSetProtection(contentProtection);
        }
      } else if (ParserUtil.isStartTag(xpp, "ContentComponent")) {
        language = checkLanguageConsistency(language, xpp.getAttributeValue(null, "lang"));
        contentType = checkContentTypeConsistency(contentType, parseContentType(xpp));
      } else if (ParserUtil.isStartTag(xpp, "Representation")) {
        Representation representation = parseRepresentation(xpp, baseUrl, mimeType, codecs, width,
            height, frameRate, audioChannels, audioSamplingRate, language, segmentBase,
            contentProtectionsBuilder);
        contentProtectionsBuilder.endRepresentation();
        contentType = checkContentTypeConsistency(contentType, getContentType(representation));
        representations.add(representation);
      } else if (ParserUtil.isStartTag(xpp, "AudioChannelConfiguration")) {
        audioChannels = parseAudioChannelConfiguration(xpp);
      } else if (ParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, baseUrl, (SingleSegmentBase) segmentBase);
      } else if (ParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, baseUrl, (SegmentList) segmentBase);
      } else if (ParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, baseUrl, (SegmentTemplate) segmentBase);
      } else if (ParserUtil.isStartTag(xpp)) {
        parseAdaptationSetChild(xpp);
      }
    } while (!ParserUtil.isEndTag(xpp, "AdaptationSet"));

    return buildAdaptationSet(id, contentType, representations, contentProtectionsBuilder.build());
  }

  protected AdaptationSet buildAdaptationSet(int id, int contentType,
      List<Representation> representations, List<ContentProtection> contentProtections) {
    return new AdaptationSet(id, contentType, representations, contentProtections);
  }

  protected int parseContentType(XmlPullParser xpp) {
    String contentType = xpp.getAttributeValue(null, "contentType");
    return TextUtils.isEmpty(contentType) ? AdaptationSet.TYPE_UNKNOWN
        : MimeTypes.BASE_TYPE_AUDIO.equals(contentType) ? AdaptationSet.TYPE_AUDIO
        : MimeTypes.BASE_TYPE_VIDEO.equals(contentType) ? AdaptationSet.TYPE_VIDEO
        : MimeTypes.BASE_TYPE_TEXT.equals(contentType) ? AdaptationSet.TYPE_TEXT
        : AdaptationSet.TYPE_UNKNOWN;
  }

  protected int getContentType(Representation representation) {
    String mimeType = representation.format.mimeType;
    if (TextUtils.isEmpty(mimeType)) {
      return AdaptationSet.TYPE_UNKNOWN;
    } else if (MimeTypes.isVideo(mimeType)) {
      return AdaptationSet.TYPE_VIDEO;
    } else if (MimeTypes.isAudio(mimeType)) {
      return AdaptationSet.TYPE_AUDIO;
    } else if (MimeTypes.isText(mimeType) || MimeTypes.APPLICATION_TTML.equals(mimeType)) {
      return AdaptationSet.TYPE_TEXT;
    } else if (MimeTypes.APPLICATION_MP4.equals(mimeType)) {
      // The representation uses mp4 but does not contain video or audio. Use codecs to determine
      // whether the container holds text.
      String codecs = representation.format.codecs;
      if ("stpp".equals(codecs) || "wvtt".equals(codecs)) {
        return AdaptationSet.TYPE_TEXT;
      }
    }
    return AdaptationSet.TYPE_UNKNOWN;
  }

  /**
   * Parses a {@link ContentProtection} element.
   *
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   * @return The parsed {@link ContentProtection} element, or null if the element is unsupported.
   **/
  protected ContentProtection parseContentProtection(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    String schemeIdUri = xpp.getAttributeValue(null, "schemeIdUri");
    UUID uuid = null;
    SchemeInitData data = null;
    boolean seenPsshElement = false;
    do {
      xpp.next();
      // The cenc:pssh element is defined in 23001-7:2015.
      if (ParserUtil.isStartTag(xpp, "cenc:pssh") && xpp.next() == XmlPullParser.TEXT) {
        seenPsshElement = true;
        data = new SchemeInitData(MimeTypes.VIDEO_MP4,
            Base64.decode(xpp.getText(), Base64.DEFAULT));
        uuid = PsshAtomUtil.parseUuid(data.data);
      }
    } while (!ParserUtil.isEndTag(xpp, "ContentProtection"));
    if (seenPsshElement && uuid == null) {
      Log.w(TAG, "Skipped unsupported ContentProtection element");
      return null;
    }
    return buildContentProtection(schemeIdUri, uuid, data);
  }

  protected ContentProtection buildContentProtection(String schemeIdUri, UUID uuid,
      SchemeInitData data) {
    return new ContentProtection(schemeIdUri, uuid, data);
  }

  /**
   * Parses children of AdaptationSet elements not specifically parsed elsewhere.
   *
   * @param xpp The XmpPullParser from which the AdaptationSet child should be parsed.
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   **/
  protected void parseAdaptationSetChild(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    // pass
  }

  // Representation parsing.

  protected Representation parseRepresentation(XmlPullParser xpp, String baseUrl,
      String adaptationSetMimeType, String adaptationSetCodecs, int adaptationSetWidth,
      int adaptationSetHeight, float adaptationSetFrameRate, int adaptationSetAudioChannels,
      int adaptationSetAudioSamplingRate, String adaptationSetLanguage, SegmentBase segmentBase,
      ContentProtectionsBuilder contentProtectionsBuilder)
      throws XmlPullParserException, IOException {
    String id = xpp.getAttributeValue(null, "id");
    int bandwidth = parseInt(xpp, "bandwidth");

    String mimeType = parseString(xpp, "mimeType", adaptationSetMimeType);
    String codecs = parseString(xpp, "codecs", adaptationSetCodecs);
    int width = parseInt(xpp, "width", adaptationSetWidth);
    int height = parseInt(xpp, "height", adaptationSetHeight);
    float frameRate = parseFrameRate(xpp, adaptationSetFrameRate);
    int audioChannels = adaptationSetAudioChannels;
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate", adaptationSetAudioSamplingRate);
    String language = adaptationSetLanguage;

    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (ParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrl = parseBaseUrl(xpp, baseUrl);
          seenFirstBaseUrl = true;
        }
      } else if (ParserUtil.isStartTag(xpp, "AudioChannelConfiguration")) {
        audioChannels = parseAudioChannelConfiguration(xpp);
      } else if (ParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, baseUrl, (SingleSegmentBase) segmentBase);
      } else if (ParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, baseUrl, (SegmentList) segmentBase);
      } else if (ParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, baseUrl, (SegmentTemplate) segmentBase);
      } else if (ParserUtil.isStartTag(xpp, "ContentProtection")) {
        ContentProtection contentProtection = parseContentProtection(xpp);
        if (contentProtection != null) {
          contentProtectionsBuilder.addAdaptationSetProtection(contentProtection);
        }
      }
    } while (!ParserUtil.isEndTag(xpp, "Representation"));

    Format format = buildFormat(id, mimeType, width, height, frameRate, audioChannels,
        audioSamplingRate, bandwidth, language, codecs);
    return buildRepresentation(contentId, -1, format,
        segmentBase != null ? segmentBase : new SingleSegmentBase(baseUrl));
  }

  protected Format buildFormat(String id, String mimeType, int width, int height, float frameRate,
      int audioChannels, int audioSamplingRate, int bandwidth, String language, String codecs) {
    return new Format(id, mimeType, width, height, frameRate, audioChannels, audioSamplingRate,
        bandwidth, language, codecs);
  }

  protected Representation buildRepresentation(String contentId, int revisionId, Format format,
      SegmentBase segmentBase) {
    return Representation.newInstance(contentId, revisionId, format, segmentBase);
  }

  // SegmentBase, SegmentList and SegmentTemplate parsing.

  protected SingleSegmentBase parseSegmentBase(XmlPullParser xpp, String baseUrl,
      SingleSegmentBase parent) throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);

    long indexStart = parent != null ? parent.indexStart : 0;
    long indexLength = parent != null ? parent.indexLength : -1;
    String indexRangeText = xpp.getAttributeValue(null, "indexRange");
    if (indexRangeText != null) {
      String[] indexRange = indexRangeText.split("-");
      indexStart = Long.parseLong(indexRange[0]);
      indexLength = Long.parseLong(indexRange[1]) - indexStart + 1;
    }

    RangedUri initialization = parent != null ? parent.initialization : null;
    do {
      xpp.next();
      if (ParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp, baseUrl);
      }
    } while (!ParserUtil.isEndTag(xpp, "SegmentBase"));

    return buildSingleSegmentBase(initialization, timescale, presentationTimeOffset, baseUrl,
        indexStart, indexLength);
  }

  protected SingleSegmentBase buildSingleSegmentBase(RangedUri initialization, long timescale,
      long presentationTimeOffset, String baseUrl, long indexStart, long indexLength) {
    return new SingleSegmentBase(initialization, timescale, presentationTimeOffset, baseUrl,
        indexStart, indexLength);
  }

  protected SegmentList parseSegmentList(XmlPullParser xpp, String baseUrl, SegmentList parent)
      throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);
    long duration = parseLong(xpp, "duration", parent != null ? parent.duration : -1);
    int startNumber = parseInt(xpp, "startNumber", parent != null ? parent.startNumber : 1);

    RangedUri initialization = null;
    List<SegmentTimelineElement> timeline = null;
    List<RangedUri> segments = null;

    do {
      xpp.next();
      if (ParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp, baseUrl);
      } else if (ParserUtil.isStartTag(xpp, "SegmentTimeline")) {
        timeline = parseSegmentTimeline(xpp);
      } else if (ParserUtil.isStartTag(xpp, "SegmentURL")) {
        if (segments == null) {
          segments = new ArrayList<>();
        }
        segments.add(parseSegmentUrl(xpp, baseUrl));
      }
    } while (!ParserUtil.isEndTag(xpp, "SegmentList"));

    if (parent != null) {
      initialization = initialization != null ? initialization : parent.initialization;
      timeline = timeline != null ? timeline : parent.segmentTimeline;
      segments = segments != null ? segments : parent.mediaSegments;
    }

    return buildSegmentList(initialization, timescale, presentationTimeOffset,
        startNumber, duration, timeline, segments);
  }

  protected SegmentList buildSegmentList(RangedUri initialization, long timescale,
      long presentationTimeOffset, int startNumber, long duration,
      List<SegmentTimelineElement> timeline, List<RangedUri> segments) {
    return new SegmentList(initialization, timescale, presentationTimeOffset,
        startNumber, duration, timeline, segments);
  }

  protected SegmentTemplate parseSegmentTemplate(XmlPullParser xpp, String baseUrl,
      SegmentTemplate parent) throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);
    long duration = parseLong(xpp, "duration", parent != null ? parent.duration : -1);
    int startNumber = parseInt(xpp, "startNumber", parent != null ? parent.startNumber : 1);
    UrlTemplate mediaTemplate = parseUrlTemplate(xpp, "media",
        parent != null ? parent.mediaTemplate : null);
    UrlTemplate initializationTemplate = parseUrlTemplate(xpp, "initialization",
        parent != null ? parent.initializationTemplate : null);

    RangedUri initialization = null;
    List<SegmentTimelineElement> timeline = null;

    do {
      xpp.next();
      if (ParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp, baseUrl);
      } else if (ParserUtil.isStartTag(xpp, "SegmentTimeline")) {
        timeline = parseSegmentTimeline(xpp);
      }
    } while (!ParserUtil.isEndTag(xpp, "SegmentTemplate"));

    if (parent != null) {
      initialization = initialization != null ? initialization : parent.initialization;
      timeline = timeline != null ? timeline : parent.segmentTimeline;
    }

    return buildSegmentTemplate(initialization, timescale, presentationTimeOffset,
        startNumber, duration, timeline, initializationTemplate, mediaTemplate, baseUrl);
  }

  protected SegmentTemplate buildSegmentTemplate(RangedUri initialization, long timescale,
      long presentationTimeOffset, int startNumber, long duration,
      List<SegmentTimelineElement> timeline, UrlTemplate initializationTemplate,
      UrlTemplate mediaTemplate, String baseUrl) {
    return new SegmentTemplate(initialization, timescale, presentationTimeOffset,
        startNumber, duration, timeline, initializationTemplate, mediaTemplate, baseUrl);
  }

  protected List<SegmentTimelineElement> parseSegmentTimeline(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    List<SegmentTimelineElement> segmentTimeline = new ArrayList<>();
    long elapsedTime = 0;
    do {
      xpp.next();
      if (ParserUtil.isStartTag(xpp, "S")) {
        elapsedTime = parseLong(xpp, "t", elapsedTime);
        long duration = parseLong(xpp, "d");
        int count = 1 + parseInt(xpp, "r", 0);
        for (int i = 0; i < count; i++) {
          segmentTimeline.add(buildSegmentTimelineElement(elapsedTime, duration));
          elapsedTime += duration;
        }
      }
    } while (!ParserUtil.isEndTag(xpp, "SegmentTimeline"));
    return segmentTimeline;
  }

  protected SegmentTimelineElement buildSegmentTimelineElement(long elapsedTime, long duration) {
    return new SegmentTimelineElement(elapsedTime, duration);
  }

  protected UrlTemplate parseUrlTemplate(XmlPullParser xpp, String name,
      UrlTemplate defaultValue) {
    String valueString = xpp.getAttributeValue(null, name);
    if (valueString != null) {
      return UrlTemplate.compile(valueString);
    }
    return defaultValue;
  }

  protected RangedUri parseInitialization(XmlPullParser xpp, String baseUrl) {
    return parseRangedUrl(xpp, baseUrl, "sourceURL", "range");
  }

  protected RangedUri parseSegmentUrl(XmlPullParser xpp, String baseUrl) {
    return parseRangedUrl(xpp, baseUrl, "media", "mediaRange");
  }

  protected RangedUri parseRangedUrl(XmlPullParser xpp, String baseUrl, String urlAttribute,
      String rangeAttribute) {
    String urlText = xpp.getAttributeValue(null, urlAttribute);
    long rangeStart = 0;
    long rangeLength = -1;
    String rangeText = xpp.getAttributeValue(null, rangeAttribute);
    if (rangeText != null) {
      String[] rangeTextArray = rangeText.split("-");
      rangeStart = Long.parseLong(rangeTextArray[0]);
      if (rangeTextArray.length == 2) {
        rangeLength = Long.parseLong(rangeTextArray[1]) - rangeStart + 1;
      }
    }
    return buildRangedUri(baseUrl, urlText, rangeStart, rangeLength);
  }

  protected RangedUri buildRangedUri(String baseUrl, String urlText, long rangeStart,
      long rangeLength) {
    return new RangedUri(baseUrl, urlText, rangeStart, rangeLength);
  }

  // AudioChannelConfiguration parsing.

  protected int parseAudioChannelConfiguration(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    int audioChannels;
    String schemeIdUri = parseString(xpp, "schemeIdUri", null);
    if ("urn:mpeg:dash:23003:3:audio_channel_configuration:2011".equals(schemeIdUri)) {
      audioChannels = parseInt(xpp, "value");
    } else {
      audioChannels = -1;
    }
    do {
      xpp.next();
    } while (!ParserUtil.isEndTag(xpp, "AudioChannelConfiguration"));
    return audioChannels;
  }

  // Utility methods.

  /**
   * Checks two languages for consistency, returning the consistent language, or throwing an
   * {@link IllegalStateException} if the languages are inconsistent.
   * <p>
   * Two languages are consistent if they are equal, or if one is null.
   *
   * @param firstLanguage The first language.
   * @param secondLanguage The second language.
   * @return The consistent language.
   */
  private static String checkLanguageConsistency(String firstLanguage, String secondLanguage) {
    if (firstLanguage == null) {
      return secondLanguage;
    } else if (secondLanguage == null) {
      return firstLanguage;
    } else {
      Assertions.checkState(firstLanguage.equals(secondLanguage));
      return firstLanguage;
    }
  }

  /**
   * Checks two adaptation set content types for consistency, returning the consistent type, or
   * throwing an {@link IllegalStateException} if the types are inconsistent.
   * <p>
   * Two types are consistent if they are equal, or if one is {@link AdaptationSet#TYPE_UNKNOWN}.
   * Where one of the types is {@link AdaptationSet#TYPE_UNKNOWN}, the other is returned.
   *
   * @param firstType The first type.
   * @param secondType The second type.
   * @return The consistent type.
   */
  private static int checkContentTypeConsistency(int firstType, int secondType) {
    if (firstType == AdaptationSet.TYPE_UNKNOWN) {
      return secondType;
    } else if (secondType == AdaptationSet.TYPE_UNKNOWN) {
      return firstType;
    } else {
      Assertions.checkState(firstType == secondType);
      return firstType;
    }
  }

  protected static float parseFrameRate(XmlPullParser xpp, float defaultValue) {
    float frameRate = defaultValue;
    String frameRateAttribute = xpp.getAttributeValue(null, "frameRate");
    if (frameRateAttribute != null) {
      Matcher frameRateMatcher = FRAME_RATE_PATTERN.matcher(frameRateAttribute);
      if (frameRateMatcher.matches()) {
        int numerator = Integer.parseInt(frameRateMatcher.group(1));
        String denominatorString = frameRateMatcher.group(2);
        if (!TextUtils.isEmpty(denominatorString)) {
          frameRate = (float) numerator / Integer.parseInt(denominatorString);
        } else {
          frameRate = numerator;
        }
      }
    }
    return frameRate;
  }

  protected static long parseDuration(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    if (value == null) {
      return defaultValue;
    } else {
      return Util.parseXsDuration(value);
    }
  }

  protected static long parseDateTime(XmlPullParser xpp, String name, long defaultValue)
      throws ParseException {
    String value = xpp.getAttributeValue(null, name);
    if (value == null) {
      return defaultValue;
    } else {
      return Util.parseXsDateTime(value);
    }
  }

  protected static String parseBaseUrl(XmlPullParser xpp, String parentBaseUrl)
      throws XmlPullParserException, IOException {
    xpp.next();
    return UriUtil.resolve(parentBaseUrl, xpp.getText());
  }

  protected static int parseInt(XmlPullParser xpp, String name) {
    return parseInt(xpp, name, -1);
  }

  protected static int parseInt(XmlPullParser xpp, String name, int defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Integer.parseInt(value);
  }

  protected static long parseLong(XmlPullParser xpp, String name) {
    return parseLong(xpp, name, -1);
  }

  protected static long parseLong(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Long.parseLong(value);
  }

  protected static String parseString(XmlPullParser xpp, String name, String defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : value;
  }

  /**
   * Builds a list of {@link ContentProtection} elements for an {@link AdaptationSet}.
   * <p>
   * If child Representation elements contain ContentProtection elements, then it is required that
   * they all define the same ones. If they do, the ContentProtection elements are bubbled up to the
   * AdaptationSet. Child Representation elements defining different ContentProtection elements is
   * considered an error.
   */
  protected static final class ContentProtectionsBuilder implements Comparator<ContentProtection> {

    private ArrayList<ContentProtection> adaptationSetProtections;
    private ArrayList<ContentProtection> representationProtections;
    private ArrayList<ContentProtection> currentRepresentationProtections;

    private boolean representationProtectionsSet;

    /**
     * Adds a {@link ContentProtection} found in the AdaptationSet element.
     *
     * @param contentProtection The {@link ContentProtection} to add.
     */
    public void addAdaptationSetProtection(ContentProtection contentProtection) {
      if (adaptationSetProtections == null) {
        adaptationSetProtections = new ArrayList<>();
      }
      maybeAddContentProtection(adaptationSetProtections, contentProtection);
    }

    /**
     * Adds a {@link ContentProtection} found in a child Representation element.
     *
     * @param contentProtection The {@link ContentProtection} to add.
     */
    public void addRepresentationProtection(ContentProtection contentProtection) {
      if (currentRepresentationProtections == null) {
        currentRepresentationProtections = new ArrayList<>();
      }
      maybeAddContentProtection(currentRepresentationProtections, contentProtection);
    }

    /**
     * Should be invoked after processing each child Representation element, in order to apply
     * consistency checks.
     */
    public void endRepresentation() {
      if (!representationProtectionsSet) {
        if (currentRepresentationProtections != null) {
          Collections.sort(currentRepresentationProtections, this);
        }
        representationProtections = currentRepresentationProtections;
        representationProtectionsSet = true;
      } else {
        // Assert that each Representation element defines the same ContentProtection elements.
        if (currentRepresentationProtections == null) {
          Assertions.checkState(representationProtections == null);
        } else {
          Collections.sort(currentRepresentationProtections, this);
          Assertions.checkState(currentRepresentationProtections.equals(representationProtections));
        }
      }
      currentRepresentationProtections = null;
    }

    /**
     * Returns the final list of consistent {@link ContentProtection} elements.
     */
    public ArrayList<ContentProtection> build() {
      if (adaptationSetProtections == null) {
        return representationProtections;
      } else if (representationProtections == null) {
        return adaptationSetProtections;
      } else {
        // Bubble up ContentProtection elements found in the child Representation elements.
        for (int i = 0; i < representationProtections.size(); i++) {
          maybeAddContentProtection(adaptationSetProtections, representationProtections.get(i));
        }
        return adaptationSetProtections;
      }
    }

    /**
     * Checks a ContentProtection for consistency with the given list, adding it if necessary.
     * <ul>
     * <li>If the new ContentProtection matches another in the list, it's consistent and is not
     *     added to the list.
     * <li>If the new ContentProtection has the same schemeUriId as another ContentProtection in the
     *     list, but its other attributes do not match, then it's inconsistent and an
     *     {@link IllegalStateException} is thrown.
     * <li>Else the new ContentProtection has a unique schemeUriId, it's consistent and is added.
     * </ul>
     *
     * @param contentProtections The list of ContentProtection elements currently known.
     * @param contentProtection The ContentProtection to add.
     */
    private void maybeAddContentProtection(List<ContentProtection> contentProtections,
        ContentProtection contentProtection) {
      if (!contentProtections.contains(contentProtection)) {
        for (int i = 0; i < contentProtections.size(); i++) {
          // If contains returned false (no complete match), but find a matching schemeUriId, then
          // the MPD contains inconsistent ContentProtection data.
          Assertions.checkState(
              !contentProtections.get(i).schemeUriId.equals(contentProtection.schemeUriId));
        }
        contentProtections.add(contentProtection);
      }
    }

    // Comparator implementation.

    @Override
    public int compare(ContentProtection first, ContentProtection second) {
      return first.schemeUriId.compareTo(second.schemeUriId);
    }

  }

}

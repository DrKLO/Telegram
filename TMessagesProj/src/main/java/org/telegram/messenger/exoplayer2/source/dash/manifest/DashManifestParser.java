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
package org.telegram.messenger.exoplayer2.source.dash.manifest;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.drm.DrmInitData;
import org.telegram.messenger.exoplayer2.drm.DrmInitData.SchemeData;
import org.telegram.messenger.exoplayer2.extractor.mp4.PsshAtomUtil;
import org.telegram.messenger.exoplayer2.source.dash.manifest.SegmentBase.SegmentList;
import org.telegram.messenger.exoplayer2.source.dash.manifest.SegmentBase.SegmentTemplate;
import org.telegram.messenger.exoplayer2.source.dash.manifest.SegmentBase.SegmentTimelineElement;
import org.telegram.messenger.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase;
import org.telegram.messenger.exoplayer2.upstream.ParsingLoadable;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.UriUtil;
import org.telegram.messenger.exoplayer2.util.Util;
import org.telegram.messenger.exoplayer2.util.XmlPullParserUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
public class DashManifestParser extends DefaultHandler
    implements ParsingLoadable.Parser<DashManifest> {

  private static final String TAG = "MpdParser";

  private static final Pattern FRAME_RATE_PATTERN = Pattern.compile("(\\d+)(?:/(\\d+))?");

  private static final Pattern CEA_608_ACCESSIBILITY_PATTERN = Pattern.compile("CC([1-4])=.*");
  private static final Pattern CEA_708_ACCESSIBILITY_PATTERN =
      Pattern.compile("([1-9]|[1-5][0-9]|6[0-3])=.*");

  private final String contentId;
  private final XmlPullParserFactory xmlParserFactory;

  /**
   * Equivalent to calling {@code new DashManifestParser(null)}.
   */
  public DashManifestParser() {
    this(null);
  }

  /**
   * @param contentId An optional content identifier to include in the parsed manifest.
   */
  public DashManifestParser(String contentId) {
    this.contentId = contentId;
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  // MPD parsing.

  @Override
  public DashManifest parse(Uri uri, InputStream inputStream) throws IOException {
    try {
      XmlPullParser xpp = xmlParserFactory.newPullParser();
      xpp.setInput(inputStream, null);
      int eventType = xpp.next();
      if (eventType != XmlPullParser.START_TAG || !"MPD".equals(xpp.getName())) {
        throw new ParserException(
            "inputStream does not contain a valid media presentation description");
      }
      return parseMediaPresentationDescription(xpp, uri.toString());
    } catch (XmlPullParserException e) {
      throw new ParserException(e);
    }
  }

  protected DashManifest parseMediaPresentationDescription(XmlPullParser xpp,
      String baseUrl) throws XmlPullParserException, IOException {
    long availabilityStartTime = parseDateTime(xpp, "availabilityStartTime", C.TIME_UNSET);
    long durationMs = parseDuration(xpp, "mediaPresentationDuration", C.TIME_UNSET);
    long minBufferTimeMs = parseDuration(xpp, "minBufferTime", C.TIME_UNSET);
    String typeString = xpp.getAttributeValue(null, "type");
    boolean dynamic = typeString != null && typeString.equals("dynamic");
    long minUpdateTimeMs = dynamic ? parseDuration(xpp, "minimumUpdatePeriod", C.TIME_UNSET)
        : C.TIME_UNSET;
    long timeShiftBufferDepthMs = dynamic
        ? parseDuration(xpp, "timeShiftBufferDepth", C.TIME_UNSET) : C.TIME_UNSET;
    long suggestedPresentationDelayMs = dynamic
        ? parseDuration(xpp, "suggestedPresentationDelay", C.TIME_UNSET) : C.TIME_UNSET;
    UtcTimingElement utcTiming = null;
    Uri location = null;

    List<Period> periods = new ArrayList<>();
    long nextPeriodStartMs = dynamic ? C.TIME_UNSET : 0;
    boolean seenEarlyAccessPeriod = false;
    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrl = parseBaseUrl(xpp, baseUrl);
          seenFirstBaseUrl = true;
        }
      } else if (XmlPullParserUtil.isStartTag(xpp, "UTCTiming")) {
        utcTiming = parseUtcTiming(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "Location")) {
        location = Uri.parse(xpp.nextText());
      } else if (XmlPullParserUtil.isStartTag(xpp, "Period") && !seenEarlyAccessPeriod) {
        Pair<Period, Long> periodWithDurationMs = parsePeriod(xpp, baseUrl, nextPeriodStartMs);
        Period period = periodWithDurationMs.first;
        if (period.startMs == C.TIME_UNSET) {
          if (dynamic) {
            // This is an early access period. Ignore it. All subsequent periods must also be
            // early access.
            seenEarlyAccessPeriod = true;
          } else {
            throw new ParserException("Unable to determine start of period " + periods.size());
          }
        } else {
          long periodDurationMs = periodWithDurationMs.second;
          nextPeriodStartMs = periodDurationMs == C.TIME_UNSET ? C.TIME_UNSET
              : (period.startMs + periodDurationMs);
          periods.add(period);
        }
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "MPD"));

    if (durationMs == C.TIME_UNSET) {
      if (nextPeriodStartMs != C.TIME_UNSET) {
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
        dynamic, minUpdateTimeMs, timeShiftBufferDepthMs, suggestedPresentationDelayMs, utcTiming,
        location, periods);
  }

  protected DashManifest buildMediaPresentationDescription(long availabilityStartTime,
      long durationMs, long minBufferTimeMs, boolean dynamic, long minUpdateTimeMs,
      long timeShiftBufferDepthMs, long suggestedPresentationDelayMs, UtcTimingElement utcTiming,
      Uri location, List<Period> periods) {
    return new DashManifest(availabilityStartTime, durationMs, minBufferTimeMs,
        dynamic, minUpdateTimeMs, timeShiftBufferDepthMs, suggestedPresentationDelayMs, utcTiming,
        location, periods);
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
    long durationMs = parseDuration(xpp, "duration", C.TIME_UNSET);
    SegmentBase segmentBase = null;
    List<AdaptationSet> adaptationSets = new ArrayList<>();
    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrl = parseBaseUrl(xpp, baseUrl);
          seenFirstBaseUrl = true;
        }
      } else if (XmlPullParserUtil.isStartTag(xpp, "AdaptationSet")) {
        adaptationSets.add(parseAdaptationSet(xpp, baseUrl, segmentBase));
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, null);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, null);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, null);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "Period"));

    return Pair.create(buildPeriod(id, startMs, adaptationSets), durationMs);
  }

  protected Period buildPeriod(String id, long startMs, List<AdaptationSet> adaptationSets) {
    return new Period(id, startMs, adaptationSets);
  }

  // AdaptationSet parsing.

  protected AdaptationSet parseAdaptationSet(XmlPullParser xpp, String baseUrl,
      SegmentBase segmentBase) throws XmlPullParserException, IOException {
    int id = parseInt(xpp, "id", AdaptationSet.ID_UNSET);
    int contentType = parseContentType(xpp);

    String mimeType = xpp.getAttributeValue(null, "mimeType");
    String codecs = xpp.getAttributeValue(null, "codecs");
    int width = parseInt(xpp, "width", Format.NO_VALUE);
    int height = parseInt(xpp, "height", Format.NO_VALUE);
    float frameRate = parseFrameRate(xpp, Format.NO_VALUE);
    int audioChannels = Format.NO_VALUE;
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate", Format.NO_VALUE);
    String language = xpp.getAttributeValue(null, "lang");
    ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();
    ArrayList<SchemeValuePair> inbandEventStreams = new ArrayList<>();
    ArrayList<SchemeValuePair> accessibilityDescriptors = new ArrayList<>();
    List<RepresentationInfo> representationInfos = new ArrayList<>();
    @C.SelectionFlags int selectionFlags = 0;

    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrl = parseBaseUrl(xpp, baseUrl);
          seenFirstBaseUrl = true;
        }
      } else if (XmlPullParserUtil.isStartTag(xpp, "ContentProtection")) {
        SchemeData contentProtection = parseContentProtection(xpp);
        if (contentProtection != null) {
          drmSchemeDatas.add(contentProtection);
        }
      } else if (XmlPullParserUtil.isStartTag(xpp, "ContentComponent")) {
        language = checkLanguageConsistency(language, xpp.getAttributeValue(null, "lang"));
        contentType = checkContentTypeConsistency(contentType, parseContentType(xpp));
      } else if (XmlPullParserUtil.isStartTag(xpp, "Role")) {
        selectionFlags |= parseRole(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "AudioChannelConfiguration")) {
        audioChannels = parseAudioChannelConfiguration(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "Accessibility")) {
        accessibilityDescriptors.add(parseAccessibility(xpp));
      } else if (XmlPullParserUtil.isStartTag(xpp, "Representation")) {
        RepresentationInfo representationInfo = parseRepresentation(xpp, baseUrl, mimeType, codecs,
            width, height, frameRate, audioChannels, audioSamplingRate, language,
            selectionFlags, accessibilityDescriptors, segmentBase);
        contentType = checkContentTypeConsistency(contentType,
            getContentType(representationInfo.format));
        representationInfos.add(representationInfo);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, (SingleSegmentBase) segmentBase);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, (SegmentList) segmentBase);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, (SegmentTemplate) segmentBase);
      } else if (XmlPullParserUtil.isStartTag(xpp, "InbandEventStream")) {
        inbandEventStreams.add(parseInbandEventStream(xpp));
      } else if (XmlPullParserUtil.isStartTag(xpp)) {
        parseAdaptationSetChild(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "AdaptationSet"));

    // Build the representations.
    List<Representation> representations = new ArrayList<>(representationInfos.size());
    for (int i = 0; i < representationInfos.size(); i++) {
      representations.add(buildRepresentation(representationInfos.get(i), contentId,
          drmSchemeDatas, inbandEventStreams));
    }

    return buildAdaptationSet(id, contentType, representations, accessibilityDescriptors);
  }

  protected AdaptationSet buildAdaptationSet(int id, int contentType,
      List<Representation> representations, List<SchemeValuePair> accessibilityDescriptors) {
    return new AdaptationSet(id, contentType, representations, accessibilityDescriptors);
  }

  protected int parseContentType(XmlPullParser xpp) {
    String contentType = xpp.getAttributeValue(null, "contentType");
    return TextUtils.isEmpty(contentType) ? C.TRACK_TYPE_UNKNOWN
        : MimeTypes.BASE_TYPE_AUDIO.equals(contentType) ? C.TRACK_TYPE_AUDIO
        : MimeTypes.BASE_TYPE_VIDEO.equals(contentType) ? C.TRACK_TYPE_VIDEO
        : MimeTypes.BASE_TYPE_TEXT.equals(contentType) ? C.TRACK_TYPE_TEXT
        : C.TRACK_TYPE_UNKNOWN;
  }

  protected int getContentType(Format format) {
    String sampleMimeType = format.sampleMimeType;
    if (TextUtils.isEmpty(sampleMimeType)) {
      return C.TRACK_TYPE_UNKNOWN;
    } else if (MimeTypes.isVideo(sampleMimeType)) {
      return C.TRACK_TYPE_VIDEO;
    } else if (MimeTypes.isAudio(sampleMimeType)) {
      return C.TRACK_TYPE_AUDIO;
    } else if (mimeTypeIsRawText(sampleMimeType)) {
      return C.TRACK_TYPE_TEXT;
    }
    return C.TRACK_TYPE_UNKNOWN;
  }

  /**
   * Parses a ContentProtection element.
   *
   * @param xpp The parser from which to read.
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   * @return {@link SchemeData} parsed from the ContentProtection element, or null if the element is
   *     unsupported.
   */
  protected SchemeData parseContentProtection(XmlPullParser xpp) throws XmlPullParserException,
      IOException {
    String schemeIdUri = xpp.getAttributeValue(null, "schemeIdUri");
    boolean isPlayReady = "urn:uuid:9a04f079-9840-4286-ab92-e65be0885f95".equals(schemeIdUri);
    byte[] data = null;
    UUID uuid = null;
    boolean requiresSecureDecoder = false;
    do {
      xpp.next();
      if (data == null && XmlPullParserUtil.isStartTag(xpp, "cenc:pssh")
          && xpp.next() == XmlPullParser.TEXT) {
        // The cenc:pssh element is defined in 23001-7:2015.
        data = Base64.decode(xpp.getText(), Base64.DEFAULT);
        uuid = PsshAtomUtil.parseUuid(data);
        if (uuid == null) {
          Log.w(TAG, "Skipping malformed cenc:pssh data");
          data = null;
        }
      } else if (data == null && isPlayReady && XmlPullParserUtil.isStartTag(xpp, "mspr:pro")
          && xpp.next() == XmlPullParser.TEXT) {
        // The mspr:pro element is defined in DASH Content Protection using Microsoft PlayReady.
        data = PsshAtomUtil.buildPsshAtom(C.PLAYREADY_UUID,
            Base64.decode(xpp.getText(), Base64.DEFAULT));
        uuid = C.PLAYREADY_UUID;
      } else if (XmlPullParserUtil.isStartTag(xpp, "widevine:license")) {
        String robustnessLevel = xpp.getAttributeValue(null, "robustness_level");
        requiresSecureDecoder = robustnessLevel != null && robustnessLevel.startsWith("HW");
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "ContentProtection"));
    return data != null ? new SchemeData(uuid, MimeTypes.VIDEO_MP4, data, requiresSecureDecoder)
        : null;
  }

  /**
   * Parses an InbandEventStream element.
   *
   * @param xpp The parser from which to read.
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   * @return A {@link SchemeValuePair} parsed from the element.
   */
  protected SchemeValuePair parseInbandEventStream(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    return parseSchemeValuePair(xpp, "InbandEventStream");
  }

  /**
   * Parses an Accessibility element.
   *
   * @param xpp The parser from which to read.
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   * @return A {@link SchemeValuePair} parsed from the element.
   */
  protected SchemeValuePair parseAccessibility(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    return parseSchemeValuePair(xpp, "Accessibility");
  }

  /**
   * Parses a Role element.
   *
   * @param xpp The parser from which to read.
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   * @return {@link C.SelectionFlags} parsed from the element.
   */
  protected int parseRole(XmlPullParser xpp) throws XmlPullParserException, IOException {
    String schemeIdUri = parseString(xpp, "schemeIdUri", null);
    String value = parseString(xpp, "value", null);
    do {
      xpp.next();
    } while (!XmlPullParserUtil.isEndTag(xpp, "Role"));
    return "urn:mpeg:dash:role:2011".equals(schemeIdUri) && "main".equals(value)
        ? C.SELECTION_FLAG_DEFAULT : 0;
  }

  /**
   * Parses children of AdaptationSet elements not specifically parsed elsewhere.
   *
   * @param xpp The XmpPullParser from which the AdaptationSet child should be parsed.
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   */
  protected void parseAdaptationSetChild(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    // pass
  }

  // Representation parsing.

  protected RepresentationInfo parseRepresentation(XmlPullParser xpp, String baseUrl,
      String adaptationSetMimeType, String adaptationSetCodecs, int adaptationSetWidth,
      int adaptationSetHeight, float adaptationSetFrameRate, int adaptationSetAudioChannels,
      int adaptationSetAudioSamplingRate, String adaptationSetLanguage,
      @C.SelectionFlags int adaptationSetSelectionFlags,
      List<SchemeValuePair> adaptationSetAccessibilityDescriptors, SegmentBase segmentBase)
      throws XmlPullParserException, IOException {
    String id = xpp.getAttributeValue(null, "id");
    int bandwidth = parseInt(xpp, "bandwidth", Format.NO_VALUE);

    String mimeType = parseString(xpp, "mimeType", adaptationSetMimeType);
    String codecs = parseString(xpp, "codecs", adaptationSetCodecs);
    int width = parseInt(xpp, "width", adaptationSetWidth);
    int height = parseInt(xpp, "height", adaptationSetHeight);
    float frameRate = parseFrameRate(xpp, adaptationSetFrameRate);
    int audioChannels = adaptationSetAudioChannels;
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate", adaptationSetAudioSamplingRate);
    ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();
    ArrayList<SchemeValuePair> inbandEventStreams = new ArrayList<>();

    boolean seenFirstBaseUrl = false;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
        if (!seenFirstBaseUrl) {
          baseUrl = parseBaseUrl(xpp, baseUrl);
          seenFirstBaseUrl = true;
        }
      } else if (XmlPullParserUtil.isStartTag(xpp, "AudioChannelConfiguration")) {
        audioChannels = parseAudioChannelConfiguration(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, (SingleSegmentBase) segmentBase);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, (SegmentList) segmentBase);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, (SegmentTemplate) segmentBase);
      } else if (XmlPullParserUtil.isStartTag(xpp, "ContentProtection")) {
        SchemeData contentProtection = parseContentProtection(xpp);
        if (contentProtection != null) {
          drmSchemeDatas.add(contentProtection);
        }
      } else if (XmlPullParserUtil.isStartTag(xpp, "InbandEventStream")) {
        inbandEventStreams.add(parseInbandEventStream(xpp));
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "Representation"));

    Format format = buildFormat(id, mimeType, width, height, frameRate, audioChannels,
        audioSamplingRate, bandwidth, adaptationSetLanguage, adaptationSetSelectionFlags,
        adaptationSetAccessibilityDescriptors, codecs);
    segmentBase = segmentBase != null ? segmentBase : new SingleSegmentBase();

    return new RepresentationInfo(format, baseUrl, segmentBase, drmSchemeDatas, inbandEventStreams);
  }

  protected Format buildFormat(String id, String containerMimeType, int width, int height,
      float frameRate, int audioChannels, int audioSamplingRate, int bitrate, String language,
      @C.SelectionFlags int selectionFlags, List<SchemeValuePair> accessibilityDescriptors,
      String codecs) {
    String sampleMimeType = getSampleMimeType(containerMimeType, codecs);
    if (sampleMimeType != null) {
      if (MimeTypes.isVideo(sampleMimeType)) {
        return Format.createVideoContainerFormat(id, containerMimeType, sampleMimeType, codecs,
            bitrate, width, height, frameRate, null, selectionFlags);
      } else if (MimeTypes.isAudio(sampleMimeType)) {
        return Format.createAudioContainerFormat(id, containerMimeType, sampleMimeType, codecs,
            bitrate, audioChannels, audioSamplingRate, null, selectionFlags, language);
      } else if (mimeTypeIsRawText(sampleMimeType)) {
        int accessibilityChannel;
        if (MimeTypes.APPLICATION_CEA608.equals(sampleMimeType)) {
          accessibilityChannel = parseCea608AccessibilityChannel(accessibilityDescriptors);
        } else if (MimeTypes.APPLICATION_CEA708.equals(sampleMimeType)) {
          accessibilityChannel = parseCea708AccessibilityChannel(accessibilityDescriptors);
        } else {
          accessibilityChannel = Format.NO_VALUE;
        }
        return Format.createTextContainerFormat(id, containerMimeType, sampleMimeType, codecs,
            bitrate, selectionFlags, language, accessibilityChannel);
      }
    }
    return Format.createContainerFormat(id, containerMimeType, sampleMimeType, codecs, bitrate,
        selectionFlags, language);
  }

  protected Representation buildRepresentation(RepresentationInfo representationInfo,
      String contentId, ArrayList<SchemeData> extraDrmSchemeDatas,
      ArrayList<SchemeValuePair> extraInbandEventStreams) {
    Format format = representationInfo.format;
    ArrayList<SchemeData> drmSchemeDatas = representationInfo.drmSchemeDatas;
    drmSchemeDatas.addAll(extraDrmSchemeDatas);
    if (!drmSchemeDatas.isEmpty()) {
      format = format.copyWithDrmInitData(new DrmInitData(drmSchemeDatas));
    }
    ArrayList<SchemeValuePair> inbandEventStremas = representationInfo.inbandEventStreams;
    inbandEventStremas.addAll(extraInbandEventStreams);
    return Representation.newInstance(contentId, Representation.REVISION_ID_DEFAULT, format,
        representationInfo.baseUrl, representationInfo.segmentBase, inbandEventStremas);
  }

  // SegmentBase, SegmentList and SegmentTemplate parsing.

  protected SingleSegmentBase parseSegmentBase(XmlPullParser xpp, SingleSegmentBase parent)
      throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);

    long indexStart = parent != null ? parent.indexStart : 0;
    long indexLength = parent != null ? parent.indexLength : 0;
    String indexRangeText = xpp.getAttributeValue(null, "indexRange");
    if (indexRangeText != null) {
      String[] indexRange = indexRangeText.split("-");
      indexStart = Long.parseLong(indexRange[0]);
      indexLength = Long.parseLong(indexRange[1]) - indexStart + 1;
    }

    RangedUri initialization = parent != null ? parent.initialization : null;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "SegmentBase"));

    return buildSingleSegmentBase(initialization, timescale, presentationTimeOffset, indexStart,
        indexLength);
  }

  protected SingleSegmentBase buildSingleSegmentBase(RangedUri initialization, long timescale,
      long presentationTimeOffset, long indexStart, long indexLength) {
    return new SingleSegmentBase(initialization, timescale, presentationTimeOffset, indexStart,
        indexLength);
  }

  protected SegmentList parseSegmentList(XmlPullParser xpp, SegmentList parent)
      throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);
    long duration = parseLong(xpp, "duration", parent != null ? parent.duration : C.TIME_UNSET);
    int startNumber = parseInt(xpp, "startNumber", parent != null ? parent.startNumber : 1);

    RangedUri initialization = null;
    List<SegmentTimelineElement> timeline = null;
    List<RangedUri> segments = null;

    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTimeline")) {
        timeline = parseSegmentTimeline(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentURL")) {
        if (segments == null) {
          segments = new ArrayList<>();
        }
        segments.add(parseSegmentUrl(xpp));
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "SegmentList"));

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

  protected SegmentTemplate parseSegmentTemplate(XmlPullParser xpp, SegmentTemplate parent)
      throws XmlPullParserException, IOException {
    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);
    long duration = parseLong(xpp, "duration", parent != null ? parent.duration : C.TIME_UNSET);
    int startNumber = parseInt(xpp, "startNumber", parent != null ? parent.startNumber : 1);
    UrlTemplate mediaTemplate = parseUrlTemplate(xpp, "media",
        parent != null ? parent.mediaTemplate : null);
    UrlTemplate initializationTemplate = parseUrlTemplate(xpp, "initialization",
        parent != null ? parent.initializationTemplate : null);

    RangedUri initialization = null;
    List<SegmentTimelineElement> timeline = null;

    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTimeline")) {
        timeline = parseSegmentTimeline(xpp);
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "SegmentTemplate"));

    if (parent != null) {
      initialization = initialization != null ? initialization : parent.initialization;
      timeline = timeline != null ? timeline : parent.segmentTimeline;
    }

    return buildSegmentTemplate(initialization, timescale, presentationTimeOffset,
        startNumber, duration, timeline, initializationTemplate, mediaTemplate);
  }

  protected SegmentTemplate buildSegmentTemplate(RangedUri initialization, long timescale,
      long presentationTimeOffset, int startNumber, long duration,
      List<SegmentTimelineElement> timeline, UrlTemplate initializationTemplate,
      UrlTemplate mediaTemplate) {
    return new SegmentTemplate(initialization, timescale, presentationTimeOffset,
        startNumber, duration, timeline, initializationTemplate, mediaTemplate);
  }

  protected List<SegmentTimelineElement> parseSegmentTimeline(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    List<SegmentTimelineElement> segmentTimeline = new ArrayList<>();
    long elapsedTime = 0;
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "S")) {
        elapsedTime = parseLong(xpp, "t", elapsedTime);
        long duration = parseLong(xpp, "d", C.TIME_UNSET);
        int count = 1 + parseInt(xpp, "r", 0);
        for (int i = 0; i < count; i++) {
          segmentTimeline.add(buildSegmentTimelineElement(elapsedTime, duration));
          elapsedTime += duration;
        }
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "SegmentTimeline"));
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

  protected RangedUri parseInitialization(XmlPullParser xpp) {
    return parseRangedUrl(xpp, "sourceURL", "range");
  }

  protected RangedUri parseSegmentUrl(XmlPullParser xpp) {
    return parseRangedUrl(xpp, "media", "mediaRange");
  }

  protected RangedUri parseRangedUrl(XmlPullParser xpp, String urlAttribute,
      String rangeAttribute) {
    String urlText = xpp.getAttributeValue(null, urlAttribute);
    long rangeStart = 0;
    long rangeLength = C.LENGTH_UNSET;
    String rangeText = xpp.getAttributeValue(null, rangeAttribute);
    if (rangeText != null) {
      String[] rangeTextArray = rangeText.split("-");
      rangeStart = Long.parseLong(rangeTextArray[0]);
      if (rangeTextArray.length == 2) {
        rangeLength = Long.parseLong(rangeTextArray[1]) - rangeStart + 1;
      }
    }
    return buildRangedUri(urlText, rangeStart, rangeLength);
  }

  protected RangedUri buildRangedUri(String urlText, long rangeStart, long rangeLength) {
    return new RangedUri(urlText, rangeStart, rangeLength);
  }

  // AudioChannelConfiguration parsing.

  protected int parseAudioChannelConfiguration(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    String schemeIdUri = parseString(xpp, "schemeIdUri", null);
    int audioChannels = "urn:mpeg:dash:23003:3:audio_channel_configuration:2011".equals(schemeIdUri)
        ? parseInt(xpp, "value", Format.NO_VALUE) : Format.NO_VALUE;
    do {
      xpp.next();
    } while (!XmlPullParserUtil.isEndTag(xpp, "AudioChannelConfiguration"));
    return audioChannels;
  }

  // Utility methods.

  /**
   * Derives a sample mimeType from a container mimeType and codecs attribute.
   *
   * @param containerMimeType The mimeType of the container.
   * @param codecs The codecs attribute.
   * @return The derived sample mimeType, or null if it could not be derived.
   */
  private static String getSampleMimeType(String containerMimeType, String codecs) {
    if (MimeTypes.isAudio(containerMimeType)) {
      return MimeTypes.getAudioMediaMimeType(codecs);
    } else if (MimeTypes.isVideo(containerMimeType)) {
      return MimeTypes.getVideoMediaMimeType(codecs);
    } else if (mimeTypeIsRawText(containerMimeType)) {
      return containerMimeType;
    } else if (MimeTypes.APPLICATION_MP4.equals(containerMimeType)) {
      if ("stpp".equals(codecs)) {
        return MimeTypes.APPLICATION_TTML;
      } else if ("wvtt".equals(codecs)) {
        return MimeTypes.APPLICATION_MP4VTT;
      }
    } else if (MimeTypes.APPLICATION_RAWCC.equals(containerMimeType)) {
      if (codecs != null) {
        if (codecs.contains("cea708")) {
          return MimeTypes.APPLICATION_CEA708;
        } else if (codecs.contains("eia608") || codecs.contains("cea608")) {
          return MimeTypes.APPLICATION_CEA608;
        }
      }
      return null;
    }
    return null;
  }

  /**
   * Returns whether a mimeType is a text sample mimeType.
   *
   * @param mimeType The mimeType.
   * @return Whether the mimeType is a text sample mimeType.
   */
  private static boolean mimeTypeIsRawText(String mimeType) {
    return MimeTypes.isText(mimeType)
        || MimeTypes.APPLICATION_TTML.equals(mimeType)
        || MimeTypes.APPLICATION_MP4VTT.equals(mimeType)
        || MimeTypes.APPLICATION_CEA708.equals(mimeType)
        || MimeTypes.APPLICATION_CEA608.equals(mimeType);
  }

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
   * Two types are consistent if they are equal, or if one is {@link C#TRACK_TYPE_UNKNOWN}.
   * Where one of the types is {@link C#TRACK_TYPE_UNKNOWN}, the other is returned.
   *
   * @param firstType The first type.
   * @param secondType The second type.
   * @return The consistent type.
   */
  private static int checkContentTypeConsistency(int firstType, int secondType) {
    if (firstType == C.TRACK_TYPE_UNKNOWN) {
      return secondType;
    } else if (secondType == C.TRACK_TYPE_UNKNOWN) {
      return firstType;
    } else {
      Assertions.checkState(firstType == secondType);
      return firstType;
    }
  }

  /**
   * Parses a {@link SchemeValuePair} from an element.
   *
   * @param xpp The parser from which to read.
   * @param tag The tag of the element being parsed.
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   * @return The parsed {@link SchemeValuePair}.
   */
  protected static SchemeValuePair parseSchemeValuePair(XmlPullParser xpp, String tag)
      throws XmlPullParserException, IOException {
    String schemeIdUri = parseString(xpp, "schemeIdUri", null);
    String value = parseString(xpp, "value", null);
    do {
      xpp.next();
    } while (!XmlPullParserUtil.isEndTag(xpp, tag));
    return new SchemeValuePair(schemeIdUri, value);
  }

  protected static int parseCea608AccessibilityChannel(
      List<SchemeValuePair> accessibilityDescriptors) {
    for (int i = 0; i < accessibilityDescriptors.size(); i++) {
      SchemeValuePair descriptor = accessibilityDescriptors.get(i);
      if ("urn:scte:dash:cc:cea-608:2015".equals(descriptor.schemeIdUri)
          && descriptor.value != null) {
        Matcher accessibilityValueMatcher = CEA_608_ACCESSIBILITY_PATTERN.matcher(descriptor.value);
        if (accessibilityValueMatcher.matches()) {
          return Integer.parseInt(accessibilityValueMatcher.group(1));
        } else {
          Log.w(TAG, "Unable to parse CEA-608 channel number from: " + descriptor.value);
        }
      }
    }
    return Format.NO_VALUE;
  }

  protected static int parseCea708AccessibilityChannel(
      List<SchemeValuePair> accessibilityDescriptors) {
    for (int i = 0; i < accessibilityDescriptors.size(); i++) {
      SchemeValuePair descriptor = accessibilityDescriptors.get(i);
      if ("urn:scte:dash:cc:cea-708:2015".equals(descriptor.schemeIdUri)
          && descriptor.value != null) {
        Matcher accessibilityValueMatcher = CEA_708_ACCESSIBILITY_PATTERN.matcher(descriptor.value);
        if (accessibilityValueMatcher.matches()) {
          return Integer.parseInt(accessibilityValueMatcher.group(1));
        } else {
          Log.w(TAG, "Unable to parse CEA-708 service block number from: " + descriptor.value);
        }
      }
    }
    return Format.NO_VALUE;
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
      throws ParserException {
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

  protected static int parseInt(XmlPullParser xpp, String name, int defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Integer.parseInt(value);
  }

  protected static long parseLong(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Long.parseLong(value);
  }

  protected static String parseString(XmlPullParser xpp, String name, String defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : value;
  }

  private static final class RepresentationInfo {

    public final Format format;
    public final String baseUrl;
    public final SegmentBase segmentBase;
    public final ArrayList<SchemeData> drmSchemeDatas;
    public final ArrayList<SchemeValuePair> inbandEventStreams;

    public RepresentationInfo(Format format, String baseUrl, SegmentBase segmentBase,
        ArrayList<SchemeData> drmSchemeDatas, ArrayList<SchemeValuePair> inbandEventStreams) {
      this.format = format;
      this.baseUrl = baseUrl;
      this.segmentBase = segmentBase;
      this.drmSchemeDatas = drmSchemeDatas;
      this.inbandEventStreams = inbandEventStreams;
    }

  }

}

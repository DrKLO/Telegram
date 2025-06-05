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
package com.google.android.exoplayer2.text.subrip;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.LongArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.base.Charsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A {@link SimpleSubtitleDecoder} for SubRip. */
public final class SubripDecoder extends SimpleSubtitleDecoder {

  // Fractional positions for use when alignment tags are present.
  private static final float START_FRACTION = 0.08f;
  private static final float END_FRACTION = 1 - START_FRACTION;
  private static final float MID_FRACTION = 0.5f;

  private static final String TAG = "SubripDecoder";

  // Some SRT files don't include hours or milliseconds in the timecode, so we use optional groups.
  private static final String SUBRIP_TIMECODE = "(?:(\\d+):)?(\\d+):(\\d+)(?:,(\\d+))?";
  private static final Pattern SUBRIP_TIMING_LINE =
      Pattern.compile("\\s*(" + SUBRIP_TIMECODE + ")\\s*-->\\s*(" + SUBRIP_TIMECODE + ")\\s*");

  // NOTE: Android Studio's suggestion to simplify '\\}' is incorrect [internal: b/144480183].
  private static final Pattern SUBRIP_TAG_PATTERN = Pattern.compile("\\{\\\\.*?\\}");
  private static final String SUBRIP_ALIGNMENT_TAG = "\\{\\\\an[1-9]\\}";

  // Alignment tags for SSA V4+.
  private static final String ALIGN_BOTTOM_LEFT = "{\\an1}";
  private static final String ALIGN_BOTTOM_MID = "{\\an2}";
  private static final String ALIGN_BOTTOM_RIGHT = "{\\an3}";
  private static final String ALIGN_MID_LEFT = "{\\an4}";
  private static final String ALIGN_MID_MID = "{\\an5}";
  private static final String ALIGN_MID_RIGHT = "{\\an6}";
  private static final String ALIGN_TOP_LEFT = "{\\an7}";
  private static final String ALIGN_TOP_MID = "{\\an8}";
  private static final String ALIGN_TOP_RIGHT = "{\\an9}";

  private final StringBuilder textBuilder;
  private final ArrayList<String> tags;

  public SubripDecoder() {
    super("SubripDecoder");
    textBuilder = new StringBuilder();
    tags = new ArrayList<>();
  }

  @Override
  protected Subtitle decode(byte[] data, int length, boolean reset) {
    ArrayList<Cue> cues = new ArrayList<>();
    LongArray cueTimesUs = new LongArray();
    ParsableByteArray subripData = new ParsableByteArray(data, length);
    Charset charset = detectUtfCharset(subripData);

    @Nullable String currentLine;
    while ((currentLine = subripData.readLine(charset)) != null) {
      if (currentLine.length() == 0) {
        // Skip blank lines.
        continue;
      }

      // Parse and check the index line.
      try {
        Integer.parseInt(currentLine);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Skipping invalid index: " + currentLine);
        continue;
      }

      // Read and parse the timing line.
      currentLine = subripData.readLine(charset);
      if (currentLine == null) {
        Log.w(TAG, "Unexpected end");
        break;
      }

      Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
      if (matcher.matches()) {
        cueTimesUs.add(parseTimecode(matcher, /* groupOffset= */ 1));
        cueTimesUs.add(parseTimecode(matcher, /* groupOffset= */ 6));
      } else {
        Log.w(TAG, "Skipping invalid timing: " + currentLine);
        continue;
      }

      // Read and parse the text and tags.
      textBuilder.setLength(0);
      tags.clear();
      currentLine = subripData.readLine(charset);
      while (!TextUtils.isEmpty(currentLine)) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(processLine(currentLine, tags));
        currentLine = subripData.readLine(charset);
      }

      Spanned text = Html.fromHtml(textBuilder.toString());

      @Nullable String alignmentTag = null;
      for (int i = 0; i < tags.size(); i++) {
        String tag = tags.get(i);
        if (tag.matches(SUBRIP_ALIGNMENT_TAG)) {
          alignmentTag = tag;
          // Subsequent alignment tags should be ignored.
          break;
        }
      }
      cues.add(buildCue(text, alignmentTag));
      cues.add(Cue.EMPTY);
    }

    Cue[] cuesArray = cues.toArray(new Cue[0]);
    long[] cueTimesUsArray = cueTimesUs.toArray();
    return new SubripSubtitle(cuesArray, cueTimesUsArray);
  }

  /**
   * Determine UTF encoding of the byte array from a byte order mark (BOM), defaulting to UTF-8 if
   * no BOM is found.
   */
  private Charset detectUtfCharset(ParsableByteArray data) {
    @Nullable Charset charset = data.readUtfCharsetFromBom();
    return charset != null ? charset : Charsets.UTF_8;
  }

  /**
   * Trims and removes tags from the given line. The removed tags are added to {@code tags}.
   *
   * @param line The line to process.
   * @param tags A list to which removed tags will be added.
   * @return The processed line.
   */
  private String processLine(String line, ArrayList<String> tags) {
    line = line.trim();

    int removedCharacterCount = 0;
    StringBuilder processedLine = new StringBuilder(line);
    Matcher matcher = SUBRIP_TAG_PATTERN.matcher(line);
    while (matcher.find()) {
      String tag = matcher.group();
      tags.add(tag);
      int start = matcher.start() - removedCharacterCount;
      int tagLength = tag.length();
      processedLine.replace(start, /* end= */ start + tagLength, /* str= */ "");
      removedCharacterCount += tagLength;
    }

    return processedLine.toString();
  }

  /**
   * Build a {@link Cue} based on the given text and alignment tag.
   *
   * @param text The text.
   * @param alignmentTag The alignment tag, or {@code null} if no alignment tag is available.
   * @return Built cue
   */
  private Cue buildCue(Spanned text, @Nullable String alignmentTag) {
    Cue.Builder cue = new Cue.Builder().setText(text);
    if (alignmentTag == null) {
      return cue.build();
    }

    // Horizontal alignment.
    switch (alignmentTag) {
      case ALIGN_BOTTOM_LEFT:
      case ALIGN_MID_LEFT:
      case ALIGN_TOP_LEFT:
        cue.setPositionAnchor(Cue.ANCHOR_TYPE_START);
        break;
      case ALIGN_BOTTOM_RIGHT:
      case ALIGN_MID_RIGHT:
      case ALIGN_TOP_RIGHT:
        cue.setPositionAnchor(Cue.ANCHOR_TYPE_END);
        break;
      case ALIGN_BOTTOM_MID:
      case ALIGN_MID_MID:
      case ALIGN_TOP_MID:
      default:
        cue.setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE);
        break;
    }

    // Vertical alignment.
    switch (alignmentTag) {
      case ALIGN_BOTTOM_LEFT:
      case ALIGN_BOTTOM_MID:
      case ALIGN_BOTTOM_RIGHT:
        cue.setLineAnchor(Cue.ANCHOR_TYPE_END);
        break;
      case ALIGN_TOP_LEFT:
      case ALIGN_TOP_MID:
      case ALIGN_TOP_RIGHT:
        cue.setLineAnchor(Cue.ANCHOR_TYPE_START);
        break;
      case ALIGN_MID_LEFT:
      case ALIGN_MID_MID:
      case ALIGN_MID_RIGHT:
      default:
        cue.setLineAnchor(Cue.ANCHOR_TYPE_MIDDLE);
        break;
    }

    return cue.setPosition(getFractionalPositionForAnchorType(cue.getPositionAnchor()))
        .setLine(getFractionalPositionForAnchorType(cue.getLineAnchor()), Cue.LINE_TYPE_FRACTION)
        .build();
  }

  private static long parseTimecode(Matcher matcher, int groupOffset) {
    @Nullable String hours = matcher.group(groupOffset + 1);
    long timestampMs = hours != null ? Long.parseLong(hours) * 60 * 60 * 1000 : 0;
    timestampMs +=
        Long.parseLong(Assertions.checkNotNull(matcher.group(groupOffset + 2))) * 60 * 1000;
    timestampMs += Long.parseLong(Assertions.checkNotNull(matcher.group(groupOffset + 3))) * 1000;
    @Nullable String millis = matcher.group(groupOffset + 4);
    if (millis != null) {
      timestampMs += Long.parseLong(millis);
    }
    return timestampMs * 1000;
  }

  /* package */ static float getFractionalPositionForAnchorType(@Cue.AnchorType int anchorType) {
    switch (anchorType) {
      case Cue.ANCHOR_TYPE_START:
        return SubripDecoder.START_FRACTION;
      case Cue.ANCHOR_TYPE_MIDDLE:
        return SubripDecoder.MID_FRACTION;
      case Cue.ANCHOR_TYPE_END:
        return SubripDecoder.END_FRACTION;
      case Cue.TYPE_UNSET:
      default:
        // Should never happen.
        throw new IllegalArgumentException();
    }
  }
}

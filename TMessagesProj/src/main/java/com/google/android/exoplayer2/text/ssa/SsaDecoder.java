/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.text.ssa;

import static com.google.android.exoplayer2.text.Cue.LINE_TYPE_FRACTION;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link SimpleSubtitleDecoder} for SSA/ASS. */
public final class SsaDecoder extends SimpleSubtitleDecoder {

  private static final String TAG = "SsaDecoder";

  private static final Pattern SSA_TIMECODE_PATTERN =
      Pattern.compile("(?:(\\d+):)?(\\d+):(\\d+)[:.](\\d+)");

  /* package */ static final String FORMAT_LINE_PREFIX = "Format:";
  /* package */ static final String STYLE_LINE_PREFIX = "Style:";
  private static final String DIALOGUE_LINE_PREFIX = "Dialogue:";

  private static final float DEFAULT_MARGIN = 0.05f;

  private final boolean haveInitializationData;
  @Nullable private final SsaDialogueFormat dialogueFormatFromInitializationData;

  private @MonotonicNonNull Map<String, SsaStyle> styles;

  /**
   * The horizontal resolution used by the subtitle author - all cue positions are relative to this.
   *
   * <p>Parsed from the {@code PlayResX} value in the {@code [Script Info]} section.
   */
  private float screenWidth;
  /**
   * The vertical resolution used by the subtitle author - all cue positions are relative to this.
   *
   * <p>Parsed from the {@code PlayResY} value in the {@code [Script Info]} section.
   */
  private float screenHeight;

  public SsaDecoder() {
    this(/* initializationData= */ null);
  }

  /**
   * Constructs an SsaDecoder with optional format and header info.
   *
   * @param initializationData Optional initialization data for the decoder. If not null or empty,
   *     the initialization data must consist of two byte arrays. The first must contain an SSA
   *     format line. The second must contain an SSA header that will be assumed common to all
   *     samples. The header is everything in an SSA file before the {@code [Events]} section (i.e.
   *     {@code [Script Info]} and optional {@code [V4+ Styles]} section.
   */
  public SsaDecoder(@Nullable List<byte[]> initializationData) {
    super("SsaDecoder");
    screenWidth = Cue.DIMEN_UNSET;
    screenHeight = Cue.DIMEN_UNSET;

    if (initializationData != null && !initializationData.isEmpty()) {
      haveInitializationData = true;
      String formatLine = Util.fromUtf8Bytes(initializationData.get(0));
      Assertions.checkArgument(formatLine.startsWith(FORMAT_LINE_PREFIX));
      dialogueFormatFromInitializationData =
          Assertions.checkNotNull(SsaDialogueFormat.fromFormatLine(formatLine));
      parseHeader(new ParsableByteArray(initializationData.get(1)));
    } else {
      haveInitializationData = false;
      dialogueFormatFromInitializationData = null;
    }
  }

  @Override
  protected Subtitle decode(byte[] data, int length, boolean reset) {
    List<List<Cue>> cues = new ArrayList<>();
    List<Long> cueTimesUs = new ArrayList<>();

    ParsableByteArray parsableData = new ParsableByteArray(data, length);
    if (!haveInitializationData) {
      parseHeader(parsableData);
    }
    parseEventBody(parsableData, cues, cueTimesUs);
    return new SsaSubtitle(cues, cueTimesUs);
  }

  /**
   * Parses the header of the subtitle.
   *
   * @param data A {@link ParsableByteArray} from which the header should be read.
   */
  private void parseHeader(ParsableByteArray data) {
    @Nullable String currentLine;
    while ((currentLine = data.readLine()) != null) {
      if ("[Script Info]".equalsIgnoreCase(currentLine)) {
        parseScriptInfo(data);
      } else if ("[V4+ Styles]".equalsIgnoreCase(currentLine)) {
        styles = parseStyles(data);
      } else if ("[V4 Styles]".equalsIgnoreCase(currentLine)) {
        Log.i(TAG, "[V4 Styles] are not supported");
      } else if ("[Events]".equalsIgnoreCase(currentLine)) {
        // We've reached the [Events] section, so the header is over.
        return;
      }
    }
  }

  /**
   * Parse the {@code [Script Info]} section.
   *
   * <p>When this returns, {@code data.position} will be set to the beginning of the first line that
   * starts with {@code [} (i.e. the title of the next section).
   *
   * @param data A {@link ParsableByteArray} with {@link ParsableByteArray#getPosition() position}
   *     set to the beginning of the first line after {@code [Script Info]}.
   */
  private void parseScriptInfo(ParsableByteArray data) {
    @Nullable String currentLine;
    while ((currentLine = data.readLine()) != null
        && (data.bytesLeft() == 0 || data.peekUnsignedByte() != '[')) {
      String[] infoNameAndValue = currentLine.split(":");
      if (infoNameAndValue.length != 2) {
        continue;
      }
      switch (Ascii.toLowerCase(infoNameAndValue[0].trim())) {
        case "playresx":
          try {
            screenWidth = Float.parseFloat(infoNameAndValue[1].trim());
          } catch (NumberFormatException e) {
            // Ignore invalid PlayResX value.
          }
          break;
        case "playresy":
          try {
            screenHeight = Float.parseFloat(infoNameAndValue[1].trim());
          } catch (NumberFormatException e) {
            // Ignore invalid PlayResY value.
          }
          break;
      }
    }
  }

  /**
   * Parse the {@code [V4+ Styles]} section.
   *
   * <p>When this returns, {@code data.position} will be set to the beginning of the first line that
   * starts with {@code [} (i.e. the title of the next section).
   *
   * @param data A {@link ParsableByteArray} with {@link ParsableByteArray#getPosition()} pointing
   *     at the beginning of the first line after {@code [V4+ Styles]}.
   */
  private static Map<String, SsaStyle> parseStyles(ParsableByteArray data) {
    Map<String, SsaStyle> styles = new LinkedHashMap<>();
    @Nullable SsaStyle.Format formatInfo = null;
    @Nullable String currentLine;
    while ((currentLine = data.readLine()) != null
        && (data.bytesLeft() == 0 || data.peekUnsignedByte() != '[')) {
      if (currentLine.startsWith(FORMAT_LINE_PREFIX)) {
        formatInfo = SsaStyle.Format.fromFormatLine(currentLine);
      } else if (currentLine.startsWith(STYLE_LINE_PREFIX)) {
        if (formatInfo == null) {
          Log.w(TAG, "Skipping 'Style:' line before 'Format:' line: " + currentLine);
          continue;
        }
        @Nullable SsaStyle style = SsaStyle.fromStyleLine(currentLine, formatInfo);
        if (style != null) {
          styles.put(style.name, style);
        }
      }
    }
    return styles;
  }

  /**
   * Parses the event body of the subtitle.
   *
   * @param data A {@link ParsableByteArray} from which the body should be read.
   * @param cues A list to which parsed cues will be added.
   * @param cueTimesUs A sorted list to which parsed cue timestamps will be added.
   */
  private void parseEventBody(ParsableByteArray data, List<List<Cue>> cues, List<Long> cueTimesUs) {
    @Nullable
    SsaDialogueFormat format = haveInitializationData ? dialogueFormatFromInitializationData : null;
    @Nullable String currentLine;
    while ((currentLine = data.readLine()) != null) {
      if (currentLine.startsWith(FORMAT_LINE_PREFIX)) {
        format = SsaDialogueFormat.fromFormatLine(currentLine);
      } else if (currentLine.startsWith(DIALOGUE_LINE_PREFIX)) {
        if (format == null) {
          Log.w(TAG, "Skipping dialogue line before complete format: " + currentLine);
          continue;
        }
        parseDialogueLine(currentLine, format, cues, cueTimesUs);
      }
    }
  }

  /**
   * Parses a dialogue line.
   *
   * @param dialogueLine The dialogue values (i.e. everything after {@code Dialogue:}).
   * @param format The dialogue format to use when parsing {@code dialogueLine}.
   * @param cues A list to which parsed cues will be added.
   * @param cueTimesUs A sorted list to which parsed cue timestamps will be added.
   */
  private void parseDialogueLine(
      String dialogueLine, SsaDialogueFormat format, List<List<Cue>> cues, List<Long> cueTimesUs) {
    Assertions.checkArgument(dialogueLine.startsWith(DIALOGUE_LINE_PREFIX));
    String[] lineValues =
        dialogueLine.substring(DIALOGUE_LINE_PREFIX.length()).split(",", format.length);
    if (lineValues.length != format.length) {
      Log.w(TAG, "Skipping dialogue line with fewer columns than format: " + dialogueLine);
      return;
    }

    long startTimeUs = parseTimecodeUs(lineValues[format.startTimeIndex]);
    if (startTimeUs == C.TIME_UNSET) {
      Log.w(TAG, "Skipping invalid timing: " + dialogueLine);
      return;
    }

    long endTimeUs = parseTimecodeUs(lineValues[format.endTimeIndex]);
    if (endTimeUs == C.TIME_UNSET) {
      Log.w(TAG, "Skipping invalid timing: " + dialogueLine);
      return;
    }

    @Nullable
    SsaStyle style =
        styles != null && format.styleIndex != C.INDEX_UNSET
            ? styles.get(lineValues[format.styleIndex].trim())
            : null;
    String rawText = lineValues[format.textIndex];
    SsaStyle.Overrides styleOverrides = SsaStyle.Overrides.parseFromDialogue(rawText);
    String text =
        SsaStyle.Overrides.stripStyleOverrides(rawText)
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", "\u00A0");
    Cue cue = createCue(text, style, styleOverrides, screenWidth, screenHeight);

    int startTimeIndex = addCuePlacerholderByTime(startTimeUs, cueTimesUs, cues);
    int endTimeIndex = addCuePlacerholderByTime(endTimeUs, cueTimesUs, cues);
    // Iterate on cues from startTimeIndex until endTimeIndex, adding the current cue.
    for (int i = startTimeIndex; i < endTimeIndex; i++) {
      cues.get(i).add(cue);
    }
  }

  /**
   * Parses an SSA timecode string.
   *
   * @param timeString The string to parse.
   * @return The parsed timestamp in microseconds.
   */
  private static long parseTimecodeUs(String timeString) {
    Matcher matcher = SSA_TIMECODE_PATTERN.matcher(timeString.trim());
    if (!matcher.matches()) {
      return C.TIME_UNSET;
    }
    long timestampUs =
        Long.parseLong(castNonNull(matcher.group(1))) * 60 * 60 * C.MICROS_PER_SECOND;
    timestampUs += Long.parseLong(castNonNull(matcher.group(2))) * 60 * C.MICROS_PER_SECOND;
    timestampUs += Long.parseLong(castNonNull(matcher.group(3))) * C.MICROS_PER_SECOND;
    timestampUs += Long.parseLong(castNonNull(matcher.group(4))) * 10000; // 100ths of a second.
    return timestampUs;
  }

  private static Cue createCue(
      String text,
      @Nullable SsaStyle style,
      SsaStyle.Overrides styleOverrides,
      float screenWidth,
      float screenHeight) {
    SpannableString spannableText = new SpannableString(text);
    Cue.Builder cue = new Cue.Builder().setText(spannableText);

    if (style != null) {
      if (style.primaryColor != null) {
        spannableText.setSpan(
            new ForegroundColorSpan(style.primaryColor),
            /* start= */ 0,
            /* end= */ spannableText.length(),
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.borderStyle == SsaStyle.SSA_BORDER_STYLE_BOX && style.outlineColor != null) {
        spannableText.setSpan(
            new BackgroundColorSpan(style.outlineColor),
            /* start= */ 0,
            /* end= */ spannableText.length(),
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.fontSize != Cue.DIMEN_UNSET && screenHeight != Cue.DIMEN_UNSET) {
        cue.setTextSize(
            style.fontSize / screenHeight, Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING);
      }
      if (style.bold && style.italic) {
        spannableText.setSpan(
            new StyleSpan(Typeface.BOLD_ITALIC),
            /* start= */ 0,
            /* end= */ spannableText.length(),
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else if (style.bold) {
        spannableText.setSpan(
            new StyleSpan(Typeface.BOLD),
            /* start= */ 0,
            /* end= */ spannableText.length(),
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else if (style.italic) {
        spannableText.setSpan(
            new StyleSpan(Typeface.ITALIC),
            /* start= */ 0,
            /* end= */ spannableText.length(),
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.underline) {
        spannableText.setSpan(
            new UnderlineSpan(),
            /* start= */ 0,
            /* end= */ spannableText.length(),
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      if (style.strikeout) {
        spannableText.setSpan(
            new StrikethroughSpan(),
            /* start= */ 0,
            /* end= */ spannableText.length(),
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    @SsaStyle.SsaAlignment int alignment;
    if (styleOverrides.alignment != SsaStyle.SSA_ALIGNMENT_UNKNOWN) {
      alignment = styleOverrides.alignment;
    } else if (style != null) {
      alignment = style.alignment;
    } else {
      alignment = SsaStyle.SSA_ALIGNMENT_UNKNOWN;
    }
    cue.setTextAlignment(toTextAlignment(alignment))
        .setPositionAnchor(toPositionAnchor(alignment))
        .setLineAnchor(toLineAnchor(alignment));

    if (styleOverrides.position != null
        && screenHeight != Cue.DIMEN_UNSET
        && screenWidth != Cue.DIMEN_UNSET) {
      cue.setPosition(styleOverrides.position.x / screenWidth);
      cue.setLine(styleOverrides.position.y / screenHeight, LINE_TYPE_FRACTION);
    } else {
      // TODO: Read the MarginL, MarginR and MarginV values from the Style & Dialogue lines.
      cue.setPosition(computeDefaultLineOrPosition(cue.getPositionAnchor()));
      cue.setLine(computeDefaultLineOrPosition(cue.getLineAnchor()), LINE_TYPE_FRACTION);
    }

    return cue.build();
  }

  @Nullable
  private static Layout.Alignment toTextAlignment(@SsaStyle.SsaAlignment int alignment) {
    switch (alignment) {
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_LEFT:
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_LEFT:
      case SsaStyle.SSA_ALIGNMENT_TOP_LEFT:
        return Layout.Alignment.ALIGN_NORMAL;
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_CENTER:
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_CENTER:
      case SsaStyle.SSA_ALIGNMENT_TOP_CENTER:
        return Layout.Alignment.ALIGN_CENTER;
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_RIGHT:
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_RIGHT:
      case SsaStyle.SSA_ALIGNMENT_TOP_RIGHT:
        return Layout.Alignment.ALIGN_OPPOSITE;
      case SsaStyle.SSA_ALIGNMENT_UNKNOWN:
        return null;
      default:
        Log.w(TAG, "Unknown alignment: " + alignment);
        return null;
    }
  }

  private static @Cue.AnchorType int toLineAnchor(@SsaStyle.SsaAlignment int alignment) {
    switch (alignment) {
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_LEFT:
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_CENTER:
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_RIGHT:
        return Cue.ANCHOR_TYPE_END;
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_LEFT:
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_CENTER:
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_RIGHT:
        return Cue.ANCHOR_TYPE_MIDDLE;
      case SsaStyle.SSA_ALIGNMENT_TOP_LEFT:
      case SsaStyle.SSA_ALIGNMENT_TOP_CENTER:
      case SsaStyle.SSA_ALIGNMENT_TOP_RIGHT:
        return Cue.ANCHOR_TYPE_START;
      case SsaStyle.SSA_ALIGNMENT_UNKNOWN:
        return Cue.TYPE_UNSET;
      default:
        Log.w(TAG, "Unknown alignment: " + alignment);
        return Cue.TYPE_UNSET;
    }
  }

  private static @Cue.AnchorType int toPositionAnchor(@SsaStyle.SsaAlignment int alignment) {
    switch (alignment) {
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_LEFT:
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_LEFT:
      case SsaStyle.SSA_ALIGNMENT_TOP_LEFT:
        return Cue.ANCHOR_TYPE_START;
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_CENTER:
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_CENTER:
      case SsaStyle.SSA_ALIGNMENT_TOP_CENTER:
        return Cue.ANCHOR_TYPE_MIDDLE;
      case SsaStyle.SSA_ALIGNMENT_BOTTOM_RIGHT:
      case SsaStyle.SSA_ALIGNMENT_MIDDLE_RIGHT:
      case SsaStyle.SSA_ALIGNMENT_TOP_RIGHT:
        return Cue.ANCHOR_TYPE_END;
      case SsaStyle.SSA_ALIGNMENT_UNKNOWN:
        return Cue.TYPE_UNSET;
      default:
        Log.w(TAG, "Unknown alignment: " + alignment);
        return Cue.TYPE_UNSET;
    }
  }

  private static float computeDefaultLineOrPosition(@Cue.AnchorType int anchor) {
    switch (anchor) {
      case Cue.ANCHOR_TYPE_START:
        return DEFAULT_MARGIN;
      case Cue.ANCHOR_TYPE_MIDDLE:
        return 0.5f;
      case Cue.ANCHOR_TYPE_END:
        return 1.0f - DEFAULT_MARGIN;
      case Cue.TYPE_UNSET:
      default:
        return Cue.DIMEN_UNSET;
    }
  }

  /**
   * Searches for {@code timeUs} in {@code sortedCueTimesUs}, inserting it if it's not found, and
   * returns the index.
   *
   * <p>If it's inserted, we also insert a matching entry to {@code cues}.
   */
  private static int addCuePlacerholderByTime(
      long timeUs, List<Long> sortedCueTimesUs, List<List<Cue>> cues) {
    int insertionIndex = 0;
    for (int i = sortedCueTimesUs.size() - 1; i >= 0; i--) {
      if (sortedCueTimesUs.get(i) == timeUs) {
        return i;
      }

      if (sortedCueTimesUs.get(i) < timeUs) {
        insertionIndex = i + 1;
        break;
      }
    }
    sortedCueTimesUs.add(insertionIndex, timeUs);
    // Copy over cues from left, or use an empty list if we're inserting at the beginning.
    cues.add(
        insertionIndex,
        insertionIndex == 0 ? new ArrayList<>() : new ArrayList<>(cues.get(insertionIndex - 1)));
    return insertionIndex;
  }
}

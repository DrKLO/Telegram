/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.text.ssa;

import static com.google.android.exoplayer2.text.ssa.SsaDecoder.STYLE_LINE_PREFIX;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.graphics.PointF;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Represents a line from an SSA/ASS {@code [V4+ Styles]} section. */
/* package */ final class SsaStyle {

  private static final String TAG = "SsaStyle";

  /**
   * The SSA/ASS alignments.
   *
   * <p>Allowed values:
   *
   * <ul>
   *   <li>{@link #SSA_ALIGNMENT_UNKNOWN}
   *   <li>{@link #SSA_ALIGNMENT_BOTTOM_LEFT}
   *   <li>{@link #SSA_ALIGNMENT_BOTTOM_CENTER}
   *   <li>{@link #SSA_ALIGNMENT_BOTTOM_RIGHT}
   *   <li>{@link #SSA_ALIGNMENT_MIDDLE_LEFT}
   *   <li>{@link #SSA_ALIGNMENT_MIDDLE_CENTER}
   *   <li>{@link #SSA_ALIGNMENT_MIDDLE_RIGHT}
   *   <li>{@link #SSA_ALIGNMENT_TOP_LEFT}
   *   <li>{@link #SSA_ALIGNMENT_TOP_CENTER}
   *   <li>{@link #SSA_ALIGNMENT_TOP_RIGHT}
   * </ul>
   */
  @IntDef({
    SSA_ALIGNMENT_UNKNOWN,
    SSA_ALIGNMENT_BOTTOM_LEFT,
    SSA_ALIGNMENT_BOTTOM_CENTER,
    SSA_ALIGNMENT_BOTTOM_RIGHT,
    SSA_ALIGNMENT_MIDDLE_LEFT,
    SSA_ALIGNMENT_MIDDLE_CENTER,
    SSA_ALIGNMENT_MIDDLE_RIGHT,
    SSA_ALIGNMENT_TOP_LEFT,
    SSA_ALIGNMENT_TOP_CENTER,
    SSA_ALIGNMENT_TOP_RIGHT,
  })
  @Documented
  @Retention(SOURCE)
  public @interface SsaAlignment {}

  // The numbering follows the ASS (v4+) spec (i.e. the points on the number pad).
  public static final int SSA_ALIGNMENT_UNKNOWN = -1;
  public static final int SSA_ALIGNMENT_BOTTOM_LEFT = 1;
  public static final int SSA_ALIGNMENT_BOTTOM_CENTER = 2;
  public static final int SSA_ALIGNMENT_BOTTOM_RIGHT = 3;
  public static final int SSA_ALIGNMENT_MIDDLE_LEFT = 4;
  public static final int SSA_ALIGNMENT_MIDDLE_CENTER = 5;
  public static final int SSA_ALIGNMENT_MIDDLE_RIGHT = 6;
  public static final int SSA_ALIGNMENT_TOP_LEFT = 7;
  public static final int SSA_ALIGNMENT_TOP_CENTER = 8;
  public static final int SSA_ALIGNMENT_TOP_RIGHT = 9;

  public final String name;
  @SsaAlignment public final int alignment;

  private SsaStyle(String name, @SsaAlignment int alignment) {
    this.name = name;
    this.alignment = alignment;
  }

  @Nullable
  public static SsaStyle fromStyleLine(String styleLine, Format format) {
    Assertions.checkArgument(styleLine.startsWith(STYLE_LINE_PREFIX));
    String[] styleValues = TextUtils.split(styleLine.substring(STYLE_LINE_PREFIX.length()), ",");
    if (styleValues.length != format.length) {
      Log.w(
          TAG,
          Util.formatInvariant(
              "Skipping malformed 'Style:' line (expected %s values, found %s): '%s'",
              format.length, styleValues.length, styleLine));
      return null;
    }
    try {
      return new SsaStyle(
          styleValues[format.nameIndex].trim(), parseAlignment(styleValues[format.alignmentIndex]));
    } catch (RuntimeException e) {
      Log.w(TAG, "Skipping malformed 'Style:' line: '" + styleLine + "'", e);
      return null;
    }
  }

  @SsaAlignment
  private static int parseAlignment(String alignmentStr) {
    try {
      @SsaAlignment int alignment = Integer.parseInt(alignmentStr.trim());
      if (isValidAlignment(alignment)) {
        return alignment;
      }
    } catch (NumberFormatException e) {
      // Swallow the exception and return UNKNOWN below.
    }
    Log.w(TAG, "Ignoring unknown alignment: " + alignmentStr);
    return SSA_ALIGNMENT_UNKNOWN;
  }

  private static boolean isValidAlignment(@SsaAlignment int alignment) {
    switch (alignment) {
      case SSA_ALIGNMENT_BOTTOM_CENTER:
      case SSA_ALIGNMENT_BOTTOM_LEFT:
      case SSA_ALIGNMENT_BOTTOM_RIGHT:
      case SSA_ALIGNMENT_MIDDLE_CENTER:
      case SSA_ALIGNMENT_MIDDLE_LEFT:
      case SSA_ALIGNMENT_MIDDLE_RIGHT:
      case SSA_ALIGNMENT_TOP_CENTER:
      case SSA_ALIGNMENT_TOP_LEFT:
      case SSA_ALIGNMENT_TOP_RIGHT:
        return true;
      case SSA_ALIGNMENT_UNKNOWN:
      default:
        return false;
    }
  }

  /**
   * Represents a {@code Format:} line from the {@code [V4+ Styles]} section
   *
   * <p>The indices are used to determine the location of particular properties in each {@code
   * Style:} line.
   */
  /* package */ static final class Format {

    public final int nameIndex;
    public final int alignmentIndex;
    public final int length;

    private Format(int nameIndex, int alignmentIndex, int length) {
      this.nameIndex = nameIndex;
      this.alignmentIndex = alignmentIndex;
      this.length = length;
    }

    /**
     * Parses the format info from a 'Format:' line in the [V4+ Styles] section.
     *
     * @return the parsed info, or null if {@code styleFormatLine} doesn't contain 'name'.
     */
    @Nullable
    public static Format fromFormatLine(String styleFormatLine) {
      int nameIndex = C.INDEX_UNSET;
      int alignmentIndex = C.INDEX_UNSET;
      String[] keys =
          TextUtils.split(styleFormatLine.substring(SsaDecoder.FORMAT_LINE_PREFIX.length()), ",");
      for (int i = 0; i < keys.length; i++) {
        switch (Util.toLowerInvariant(keys[i].trim())) {
          case "name":
            nameIndex = i;
            break;
          case "alignment":
            alignmentIndex = i;
            break;
        }
      }
      return nameIndex != C.INDEX_UNSET ? new Format(nameIndex, alignmentIndex, keys.length) : null;
    }
  }

  /**
   * Represents the style override information parsed from an SSA/ASS dialogue line.
   *
   * <p>Overrides are contained in braces embedded in the dialogue text of the cue.
   */
  /* package */ static final class Overrides {

    private static final String TAG = "SsaStyle.Overrides";

    /** Matches "{foo}" and returns "foo" in group 1 */
    // Warning that \\} can be replaced with } is bogus [internal: b/144480183].
    private static final Pattern BRACES_PATTERN = Pattern.compile("\\{([^}]*)\\}");

    private static final String PADDED_DECIMAL_PATTERN = "\\s*\\d+(?:\\.\\d+)?\\s*";

    /** Matches "\pos(x,y)" and returns "x" in group 1 and "y" in group 2 */
    private static final Pattern POSITION_PATTERN =
        Pattern.compile(Util.formatInvariant("\\\\pos\\((%1$s),(%1$s)\\)", PADDED_DECIMAL_PATTERN));
    /** Matches "\move(x1,y1,x2,y2[,t1,t2])" and returns "x2" in group 1 and "y2" in group 2 */
    private static final Pattern MOVE_PATTERN =
        Pattern.compile(
            Util.formatInvariant(
                "\\\\move\\(%1$s,%1$s,(%1$s),(%1$s)(?:,%1$s,%1$s)?\\)", PADDED_DECIMAL_PATTERN));

    /** Matches "\anx" and returns x in group 1 */
    private static final Pattern ALIGNMENT_OVERRIDE_PATTERN = Pattern.compile("\\\\an(\\d+)");

    @SsaAlignment public final int alignment;
    @Nullable public final PointF position;

    private Overrides(@SsaAlignment int alignment, @Nullable PointF position) {
      this.alignment = alignment;
      this.position = position;
    }

    public static Overrides parseFromDialogue(String text) {
      @SsaAlignment int alignment = SSA_ALIGNMENT_UNKNOWN;
      PointF position = null;
      Matcher matcher = BRACES_PATTERN.matcher(text);
      while (matcher.find()) {
        String braceContents = matcher.group(1);
        try {
          PointF parsedPosition = parsePosition(braceContents);
          if (parsedPosition != null) {
            position = parsedPosition;
          }
        } catch (RuntimeException e) {
          // Ignore invalid \pos() or \move() function.
        }
        try {
          @SsaAlignment int parsedAlignment = parseAlignmentOverride(braceContents);
          if (parsedAlignment != SSA_ALIGNMENT_UNKNOWN) {
            alignment = parsedAlignment;
          }
        } catch (RuntimeException e) {
          // Ignore invalid \an alignment override.
        }
      }
      return new Overrides(alignment, position);
    }

    public static String stripStyleOverrides(String dialogueLine) {
      return BRACES_PATTERN.matcher(dialogueLine).replaceAll("");
    }

    /**
     * Parses the position from a style override, returns null if no position is found.
     *
     * <p>The attribute is expected to be in the form {@code \pos(x,y)} or {@code
     * \move(x1,y1,x2,y2,startTime,endTime)} (startTime and endTime are optional). In the case of
     * {@code \move()}, this returns {@code (x2, y2)} (i.e. the end position of the move).
     *
     * @param styleOverride The string to parse.
     * @return The parsed position, or null if no position is found.
     */
    @Nullable
    private static PointF parsePosition(String styleOverride) {
      Matcher positionMatcher = POSITION_PATTERN.matcher(styleOverride);
      Matcher moveMatcher = MOVE_PATTERN.matcher(styleOverride);
      boolean hasPosition = positionMatcher.find();
      boolean hasMove = moveMatcher.find();

      String x;
      String y;
      if (hasPosition) {
        if (hasMove) {
          Log.i(
              TAG,
              "Override has both \\pos(x,y) and \\move(x1,y1,x2,y2); using \\pos values. override='"
                  + styleOverride
                  + "'");
        }
        x = positionMatcher.group(1);
        y = positionMatcher.group(2);
      } else if (hasMove) {
        x = moveMatcher.group(1);
        y = moveMatcher.group(2);
      } else {
        return null;
      }
      return new PointF(
          Float.parseFloat(Assertions.checkNotNull(x).trim()),
          Float.parseFloat(Assertions.checkNotNull(y).trim()));
    }

    @SsaAlignment
    private static int parseAlignmentOverride(String braceContents) {
      Matcher matcher = ALIGNMENT_OVERRIDE_PATTERN.matcher(braceContents);
      return matcher.find() ? parseAlignment(matcher.group(1)) : SSA_ALIGNMENT_UNKNOWN;
    }
  }
}

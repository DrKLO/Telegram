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
package com.google.android.exoplayer2.text.webvtt;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.text.Layout.Alignment;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/** A representation of a WebVTT cue. */
public final class WebvttCue extends Cue {

  private static final float DEFAULT_POSITION = 0.5f;

  public final long startTime;
  public final long endTime;

  private WebvttCue(
      long startTime,
      long endTime,
      CharSequence text,
      @Nullable Alignment textAlignment,
      float line,
      @Cue.LineType int lineType,
      @Cue.AnchorType int lineAnchor,
      float position,
      @Cue.AnchorType int positionAnchor,
      float width) {
    super(text, textAlignment, line, lineType, lineAnchor, position, positionAnchor, width);
    this.startTime = startTime;
    this.endTime = endTime;
  }

  /**
   * Returns whether or not this cue should be placed in the default position and rolled-up with
   * the other "normal" cues.
   *
   * @return Whether this cue should be placed in the default position.
   */
  public boolean isNormalCue() {
    return (line == DIMEN_UNSET && position == DEFAULT_POSITION);
  }

  /** Builder for WebVTT cues. */
  @SuppressWarnings("hiding")
  public static class Builder {

    /**
     * Valid values for {@link #setTextAlignment(int)}.
     *
     * <p>We use a custom list (and not {@link Alignment} directly) in order to include both {@code
     * START}/{@code LEFT} and {@code END}/{@code RIGHT}. The distinction is important for {@link
     * #derivePosition(int)}.
     *
     * <p>These correspond to the valid values for the 'align' cue setting in the <a
     * href="https://www.w3.org/TR/webvtt1/#webvtt-cue-text-alignment">WebVTT spec</a>.
     */
    @Documented
    @Retention(SOURCE)
    @IntDef({
        TEXT_ALIGNMENT_START,
        TEXT_ALIGNMENT_CENTER,
        TEXT_ALIGNMENT_END,
        TEXT_ALIGNMENT_LEFT,
        TEXT_ALIGNMENT_RIGHT
    })
    public @interface TextAlignment {}
    /**
     * See WebVTT's <a
     * href="https://www.w3.org/TR/webvtt1/#webvtt-cue-start-alignment">align:start</a>.
     */
    public static final int TEXT_ALIGNMENT_START = 1;

    /**
     * See WebVTT's <a
     * href="https://www.w3.org/TR/webvtt1/#webvtt-cue-center-alignment">align:center</a>.
     */
    public static final int TEXT_ALIGNMENT_CENTER = 2;

    /**
     * See WebVTT's <a href="https://www.w3.org/TR/webvtt1/#webvtt-cue-end-alignment">align:end</a>.
     */
    public static final int TEXT_ALIGNMENT_END = 3;

    /**
     * See WebVTT's <a href="https://www.w3.org/TR/webvtt1/#webvtt-cue-left-alignment">align:left</a>.
     */
    public static final int TEXT_ALIGNMENT_LEFT = 4;

    /**
     * See WebVTT's <a
     * href="https://www.w3.org/TR/webvtt1/#webvtt-cue-right-alignment">align:right</a>.
     */
    public static final int TEXT_ALIGNMENT_RIGHT = 5;

    private static final String TAG = "WebvttCueBuilder";

    private long startTime;
    private long endTime;
    @Nullable private CharSequence text;
    @TextAlignment private int textAlignment;
    private float line;
    // Equivalent to WebVTT's snap-to-lines flag:
    // https://www.w3.org/TR/webvtt1/#webvtt-cue-snap-to-lines-flag
    @LineType private int lineType;
    @AnchorType private int lineAnchor;
    private float position;
    @AnchorType private int positionAnchor;
    private float width;

    // Initialization methods

    // Calling reset() is forbidden because `this` isn't initialized. This can be safely
    // suppressed because reset() only assigns fields, it doesn't read any.
    @SuppressWarnings("nullness:method.invocation.invalid")
    public Builder() {
      reset();
    }

    public void reset() {
      startTime = 0;
      endTime = 0;
      text = null;
      // Default: https://www.w3.org/TR/webvtt1/#webvtt-cue-text-alignment
      textAlignment = TEXT_ALIGNMENT_CENTER;
      line = Cue.DIMEN_UNSET;
      // Defaults to NUMBER (true): https://www.w3.org/TR/webvtt1/#webvtt-cue-snap-to-lines-flag
      lineType = Cue.LINE_TYPE_NUMBER;
      // Default: https://www.w3.org/TR/webvtt1/#webvtt-cue-line-alignment
      lineAnchor = Cue.ANCHOR_TYPE_START;
      position = Cue.DIMEN_UNSET;
      positionAnchor = Cue.TYPE_UNSET;
      // Default: https://www.w3.org/TR/webvtt1/#webvtt-cue-size
      width = 1.0f;
    }

    // Construction methods.

    public WebvttCue build() {
      line = computeLine(line, lineType);

      if (position == Cue.DIMEN_UNSET) {
        position = derivePosition(textAlignment);
      }

      if (positionAnchor == Cue.TYPE_UNSET) {
        positionAnchor = derivePositionAnchor(textAlignment);
      }

      width = Math.min(width, deriveMaxSize(positionAnchor, position));

      return new WebvttCue(
          startTime,
          endTime,
          Assertions.checkNotNull(text),
          convertTextAlignment(textAlignment),
          line,
          lineType,
          lineAnchor,
          position,
          positionAnchor,
          width);
    }

    public Builder setStartTime(long time) {
      startTime = time;
      return this;
    }

    public Builder setEndTime(long time) {
      endTime = time;
      return this;
    }

    public Builder setText(CharSequence text) {
      this.text = text;
      return this;
    }

    public Builder setTextAlignment(@TextAlignment int textAlignment) {
      this.textAlignment = textAlignment;
      return this;
    }

    public Builder setLine(float line) {
      this.line = line;
      return this;
    }

    public Builder setLineType(@LineType int lineType) {
      this.lineType = lineType;
      return this;
    }

    public Builder setLineAnchor(@AnchorType int lineAnchor) {
      this.lineAnchor = lineAnchor;
      return this;
    }

    public Builder setPosition(float position) {
      this.position = position;
      return this;
    }

    public Builder setPositionAnchor(@AnchorType int positionAnchor) {
      this.positionAnchor = positionAnchor;
      return this;
    }

    public Builder setWidth(float width) {
      this.width = width;
      return this;
    }

    // https://www.w3.org/TR/webvtt1/#webvtt-cue-line
    private static float computeLine(float line, @LineType int lineType) {
      if (line != Cue.DIMEN_UNSET
          && lineType == Cue.LINE_TYPE_FRACTION
          && (line < 0.0f || line > 1.0f)) {
        return 1.0f; // Step 1
      } else if (line != Cue.DIMEN_UNSET) {
        // Step 2: Do nothing, line is already correct.
        return line;
      } else if (lineType == Cue.LINE_TYPE_FRACTION) {
        return 1.0f; // Step 3
      } else {
        // Steps 4 - 10 (stacking multiple simultaneous cues) are handled by WebvttSubtitle#getCues
        // and WebvttCue#isNormalCue.
        return DIMEN_UNSET;
      }
    }

    // https://www.w3.org/TR/webvtt1/#webvtt-cue-position
    private static float derivePosition(@TextAlignment int textAlignment) {
      switch (textAlignment) {
        case TEXT_ALIGNMENT_LEFT:
          return 0.0f;
        case TEXT_ALIGNMENT_RIGHT:
          return 1.0f;
        case TEXT_ALIGNMENT_START:
        case TEXT_ALIGNMENT_CENTER:
        case TEXT_ALIGNMENT_END:
        default:
          return DEFAULT_POSITION;
      }
    }

    // https://www.w3.org/TR/webvtt1/#webvtt-cue-position-alignment
    @AnchorType
    private static int derivePositionAnchor(@TextAlignment int textAlignment) {
      switch (textAlignment) {
        case TEXT_ALIGNMENT_LEFT:
        case TEXT_ALIGNMENT_START:
          return Cue.ANCHOR_TYPE_START;
        case TEXT_ALIGNMENT_RIGHT:
        case TEXT_ALIGNMENT_END:
          return Cue.ANCHOR_TYPE_END;
        case TEXT_ALIGNMENT_CENTER:
        default:
          return Cue.ANCHOR_TYPE_MIDDLE;
      }
    }

    @Nullable
    private static Alignment convertTextAlignment(@TextAlignment int textAlignment) {
      switch (textAlignment) {
        case TEXT_ALIGNMENT_START:
        case TEXT_ALIGNMENT_LEFT:
          return Alignment.ALIGN_NORMAL;
        case TEXT_ALIGNMENT_CENTER:
          return Alignment.ALIGN_CENTER;
        case TEXT_ALIGNMENT_END:
        case TEXT_ALIGNMENT_RIGHT:
          return Alignment.ALIGN_OPPOSITE;
        default:
          Log.w(TAG, "Unknown textAlignment: " + textAlignment);
          return null;
      }
    }

    // Step 2 here: https://www.w3.org/TR/webvtt1/#processing-cue-settings
    private static float deriveMaxSize(@AnchorType int positionAnchor, float position) {
      switch (positionAnchor) {
        case Cue.ANCHOR_TYPE_START:
          return 1.0f - position;
        case Cue.ANCHOR_TYPE_END:
          return position;
        case Cue.ANCHOR_TYPE_MIDDLE:
          if (position <= 0.5f) {
            return position * 2;
          } else {
            return (1.0f - position) * 2;
          }
        case Cue.TYPE_UNSET:
        default:
          throw new IllegalStateException(String.valueOf(positionAnchor));
      }
    }
  }
}

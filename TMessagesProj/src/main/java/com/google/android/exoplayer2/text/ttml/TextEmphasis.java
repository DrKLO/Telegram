/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.text.ttml;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.android.exoplayer2.text.span.TextEmphasisSpan;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Represents a <a
 * href="https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-textEmphasis">
 * tts:textEmphasis</a> attribute.
 */
/* package */ final class TextEmphasis {

  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    TextEmphasisSpan.MARK_SHAPE_NONE,
    TextEmphasisSpan.MARK_SHAPE_CIRCLE,
    TextEmphasisSpan.MARK_SHAPE_DOT,
    TextEmphasisSpan.MARK_SHAPE_SESAME,
    MARK_SHAPE_AUTO
  })
  @interface MarkShape {}

  /**
   * The "auto" mark shape is only defined in TTML and is resolved to a concrete shape when building
   * the {@link Cue}. Hence, it is not defined in {@link TextEmphasisSpan.MarkShape}.
   */
  public static final int MARK_SHAPE_AUTO = -1;

  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    TextAnnotation.POSITION_UNKNOWN,
    TextAnnotation.POSITION_BEFORE,
    TextAnnotation.POSITION_AFTER,
    POSITION_OUTSIDE
  })
  public @interface Position {}

  /**
   * The "outside" position is only defined in TTML and is resolved before outputting a {@link Cue}
   * object. Hence, it is not defined in {@link TextAnnotation.Position}.
   */
  public static final int POSITION_OUTSIDE = -2;

  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  private static final ImmutableSet<String> SINGLE_STYLE_VALUES =
      ImmutableSet.of(TtmlNode.TEXT_EMPHASIS_AUTO, TtmlNode.TEXT_EMPHASIS_NONE);

  private static final ImmutableSet<String> MARK_SHAPE_VALUES =
      ImmutableSet.of(
          TtmlNode.TEXT_EMPHASIS_MARK_DOT,
          TtmlNode.TEXT_EMPHASIS_MARK_SESAME,
          TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE);

  private static final ImmutableSet<String> MARK_FILL_VALUES =
      ImmutableSet.of(TtmlNode.TEXT_EMPHASIS_MARK_FILLED, TtmlNode.TEXT_EMPHASIS_MARK_OPEN);

  private static final ImmutableSet<String> POSITION_VALUES =
      ImmutableSet.of(
          TtmlNode.ANNOTATION_POSITION_AFTER,
          TtmlNode.ANNOTATION_POSITION_BEFORE,
          TtmlNode.ANNOTATION_POSITION_OUTSIDE);

  /** The text emphasis mark shape. */
  public final @MarkShape int markShape;

  /** The fill style of the text emphasis mark. */
  public final @TextEmphasisSpan.MarkFill int markFill;

  /** The position of the text emphasis relative to the base text. */
  public final @Position int position;

  private TextEmphasis(
      @MarkShape int markShape,
      @TextEmphasisSpan.MarkFill int markFill,
      @TextAnnotation.Position int position) {
    this.markShape = markShape;
    this.markFill = markFill;
    this.position = position;
  }

  /**
   * Parses a TTML <a
   * href="https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-textEmphasis">
   * tts:textEmphasis</a> attribute. Returns null if parsing fails.
   *
   * <p>The parser searches for {@code emphasis-style} and {@code emphasis-position} independently.
   * If a valid style is not found, the default style is used. If a valid position is not found, the
   * default position is used.
   *
   * <p>Not implemented:
   *
   * <ul>
   *   <li>{@code emphasis-color}
   *   <li>Quoted string {@code emphasis-style}
   * </ul>
   */
  @Nullable
  public static TextEmphasis parse(@Nullable String value) {
    if (value == null) {
      return null;
    }

    String parsingValue = Ascii.toLowerCase(value.trim());
    if (parsingValue.isEmpty()) {
      return null;
    }

    return parseWords(ImmutableSet.copyOf(TextUtils.split(parsingValue, WHITESPACE_PATTERN)));
  }

  private static TextEmphasis parseWords(ImmutableSet<String> nodes) {
    Set<String> matchingPositions = Sets.intersection(POSITION_VALUES, nodes);
    // If no emphasis position is specified, then the emphasis position must be interpreted as if
    // a position of outside were specified:
    // https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-textEmphasis
    @Position int position;
    switch (Iterables.getFirst(matchingPositions, TtmlNode.ANNOTATION_POSITION_OUTSIDE)) {
      case TtmlNode.ANNOTATION_POSITION_AFTER:
        position = TextAnnotation.POSITION_AFTER;
        break;
      case TtmlNode.ANNOTATION_POSITION_OUTSIDE:
        position = POSITION_OUTSIDE;
        break;
      case TtmlNode.ANNOTATION_POSITION_BEFORE:
      default:
        // If an implementation does not recognize or otherwise distinguish an annotation position
        // value, then it must be interpreted as if a position of 'before' were specified:
        // https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-textEmphasis
        position = TextAnnotation.POSITION_BEFORE;
    }

    Set<String> matchingSingleStyles = Sets.intersection(SINGLE_STYLE_VALUES, nodes);
    if (!matchingSingleStyles.isEmpty()) {
      // If "none" or "auto" are found in the description, ignore the other style (fill, shape)
      // attributes.
      @MarkShape int markShape;
      switch (matchingSingleStyles.iterator().next()) {
        case TtmlNode.TEXT_EMPHASIS_NONE:
          markShape = TextEmphasisSpan.MARK_SHAPE_NONE;
          break;
        case TtmlNode.TEXT_EMPHASIS_AUTO:
        default:
          markShape = MARK_SHAPE_AUTO;
      }
      // markFill is ignored when markShape is NONE or AUTO
      return new TextEmphasis(markShape, TextEmphasisSpan.MARK_FILL_UNKNOWN, position);
    }

    Set<String> matchingFills = Sets.intersection(MARK_FILL_VALUES, nodes);
    Set<String> matchingShapes = Sets.intersection(MARK_SHAPE_VALUES, nodes);
    if (matchingFills.isEmpty() && matchingShapes.isEmpty()) {
      // If an implementation does not recognize or otherwise distinguish an emphasis style value,
      // then it must be interpreted as if a style of auto were specified; as such, an
      // implementation that supports text emphasis marks must minimally support the auto value.
      // https://www.w3.org/TR/ttml2/#style-value-emphasis-style.
      //
      // markFill is ignored when markShape is NONE or AUTO.
      return new TextEmphasis(MARK_SHAPE_AUTO, TextEmphasisSpan.MARK_FILL_UNKNOWN, position);
    }

    @TextEmphasisSpan.MarkFill int markFill;
    switch (Iterables.getFirst(matchingFills, TtmlNode.TEXT_EMPHASIS_MARK_FILLED)) {
      case TtmlNode.TEXT_EMPHASIS_MARK_OPEN:
        markFill = TextEmphasisSpan.MARK_FILL_OPEN;
        break;
      case TtmlNode.TEXT_EMPHASIS_MARK_FILLED:
      default:
        markFill = TextEmphasisSpan.MARK_FILL_FILLED;
    }

    @MarkShape int markShape;
    switch (Iterables.getFirst(matchingShapes, TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE)) {
      case TtmlNode.TEXT_EMPHASIS_MARK_DOT:
        markShape = TextEmphasisSpan.MARK_SHAPE_DOT;
        break;
      case TtmlNode.TEXT_EMPHASIS_MARK_SESAME:
        markShape = TextEmphasisSpan.MARK_SHAPE_SESAME;
        break;
      case TtmlNode.TEXT_EMPHASIS_MARK_CIRCLE:
      default:
        markShape = TextEmphasisSpan.MARK_SHAPE_CIRCLE;
    }

    return new TextEmphasis(markShape, markFill, position);
  }
}

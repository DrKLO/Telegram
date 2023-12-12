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
package com.google.android.exoplayer2.text.ttml;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Typeface;
import android.text.Layout;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.span.TextAnnotation;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Style object of a <code>TtmlNode</code> */
/* package */ final class TtmlStyle {

  public static final int UNSPECIFIED = -1;
  public static final float UNSPECIFIED_SHEAR = Float.MAX_VALUE;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {UNSPECIFIED, STYLE_NORMAL, STYLE_BOLD, STYLE_ITALIC, STYLE_BOLD_ITALIC})
  public @interface StyleFlags {}

  public static final int STYLE_NORMAL = Typeface.NORMAL;
  public static final int STYLE_BOLD = Typeface.BOLD;
  public static final int STYLE_ITALIC = Typeface.ITALIC;
  public static final int STYLE_BOLD_ITALIC = Typeface.BOLD_ITALIC;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({UNSPECIFIED, FONT_SIZE_UNIT_PIXEL, FONT_SIZE_UNIT_EM, FONT_SIZE_UNIT_PERCENT})
  public @interface FontSizeUnit {}

  public static final int FONT_SIZE_UNIT_PIXEL = 1;
  public static final int FONT_SIZE_UNIT_EM = 2;
  public static final int FONT_SIZE_UNIT_PERCENT = 3;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({UNSPECIFIED, OFF, ON})
  private @interface OptionalBoolean {}

  private static final int OFF = 0;
  private static final int ON = 1;

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({UNSPECIFIED, RUBY_TYPE_CONTAINER, RUBY_TYPE_BASE, RUBY_TYPE_TEXT, RUBY_TYPE_DELIMITER})
  public @interface RubyType {}

  public static final int RUBY_TYPE_CONTAINER = 1;
  public static final int RUBY_TYPE_BASE = 2;
  public static final int RUBY_TYPE_TEXT = 3;
  public static final int RUBY_TYPE_DELIMITER = 4;

  @Nullable private String fontFamily;
  private int fontColor;
  private boolean hasFontColor;
  private int backgroundColor;
  private boolean hasBackgroundColor;
  private @OptionalBoolean int linethrough;
  private @OptionalBoolean int underline;
  private @OptionalBoolean int bold;
  private @OptionalBoolean int italic;
  private @FontSizeUnit int fontSizeUnit;
  private float fontSize;
  @Nullable private String id;
  private @RubyType int rubyType;
  private @TextAnnotation.Position int rubyPosition;
  @Nullable private Layout.Alignment textAlign;
  @Nullable private Layout.Alignment multiRowAlign;
  private @OptionalBoolean int textCombine;
  @Nullable private TextEmphasis textEmphasis;
  private float shearPercentage;

  public TtmlStyle() {
    linethrough = UNSPECIFIED;
    underline = UNSPECIFIED;
    bold = UNSPECIFIED;
    italic = UNSPECIFIED;
    fontSizeUnit = UNSPECIFIED;
    rubyType = UNSPECIFIED;
    rubyPosition = TextAnnotation.POSITION_UNKNOWN;
    textCombine = UNSPECIFIED;
    shearPercentage = UNSPECIFIED_SHEAR;
  }

  /**
   * Returns the style or {@link #UNSPECIFIED} when no style information is given.
   *
   * @return {@link #UNSPECIFIED}, {@link #STYLE_NORMAL}, {@link #STYLE_BOLD}, {@link #STYLE_BOLD}
   *     or {@link #STYLE_BOLD_ITALIC}.
   */
  public @StyleFlags int getStyle() {
    if (bold == UNSPECIFIED && italic == UNSPECIFIED) {
      return UNSPECIFIED;
    }
    return (bold == ON ? STYLE_BOLD : STYLE_NORMAL) | (italic == ON ? STYLE_ITALIC : STYLE_NORMAL);
  }

  public boolean isLinethrough() {
    return linethrough == ON;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setLinethrough(boolean linethrough) {
    this.linethrough = linethrough ? ON : OFF;
    return this;
  }

  public boolean isUnderline() {
    return underline == ON;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setUnderline(boolean underline) {
    this.underline = underline ? ON : OFF;
    return this;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setBold(boolean bold) {
    this.bold = bold ? ON : OFF;
    return this;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setItalic(boolean italic) {
    this.italic = italic ? ON : OFF;
    return this;
  }

  @Nullable
  public String getFontFamily() {
    return fontFamily;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setFontFamily(@Nullable String fontFamily) {
    this.fontFamily = fontFamily;
    return this;
  }

  public int getFontColor() {
    if (!hasFontColor) {
      throw new IllegalStateException("Font color has not been defined.");
    }
    return fontColor;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setFontColor(int fontColor) {
    this.fontColor = fontColor;
    hasFontColor = true;
    return this;
  }

  public boolean hasFontColor() {
    return hasFontColor;
  }

  public int getBackgroundColor() {
    if (!hasBackgroundColor) {
      throw new IllegalStateException("Background color has not been defined.");
    }
    return backgroundColor;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setBackgroundColor(int backgroundColor) {
    this.backgroundColor = backgroundColor;
    hasBackgroundColor = true;
    return this;
  }

  public boolean hasBackgroundColor() {
    return hasBackgroundColor;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setShearPercentage(float shearPercentage) {
    this.shearPercentage = shearPercentage;
    return this;
  }

  public float getShearPercentage() {
    return shearPercentage;
  }

  /**
   * Chains this style to referential style. Local properties which are already set are never
   * overridden.
   *
   * @param ancestor the referential style to inherit from
   */
  @CanIgnoreReturnValue
  public TtmlStyle chain(@Nullable TtmlStyle ancestor) {
    return inherit(ancestor, true);
  }

  /**
   * Inherits from an ancestor style. Properties like <i>tts:backgroundColor</i> which are not
   * inheritable are not inherited as well as properties which are already set locally are never
   * overridden.
   *
   * @param ancestor the ancestor style to inherit from
   */
  @CanIgnoreReturnValue
  public TtmlStyle inherit(@Nullable TtmlStyle ancestor) {
    return inherit(ancestor, false);
  }

  @CanIgnoreReturnValue
  private TtmlStyle inherit(@Nullable TtmlStyle ancestor, boolean chaining) {
    if (ancestor != null) {
      if (!hasFontColor && ancestor.hasFontColor) {
        setFontColor(ancestor.fontColor);
      }
      if (bold == UNSPECIFIED) {
        bold = ancestor.bold;
      }
      if (italic == UNSPECIFIED) {
        italic = ancestor.italic;
      }
      if (fontFamily == null && ancestor.fontFamily != null) {
        fontFamily = ancestor.fontFamily;
      }
      if (linethrough == UNSPECIFIED) {
        linethrough = ancestor.linethrough;
      }
      if (underline == UNSPECIFIED) {
        underline = ancestor.underline;
      }
      if (rubyPosition == TextAnnotation.POSITION_UNKNOWN) {
        rubyPosition = ancestor.rubyPosition;
      }
      if (textAlign == null && ancestor.textAlign != null) {
        textAlign = ancestor.textAlign;
      }
      if (multiRowAlign == null && ancestor.multiRowAlign != null) {
        multiRowAlign = ancestor.multiRowAlign;
      }
      if (textCombine == UNSPECIFIED) {
        textCombine = ancestor.textCombine;
      }
      if (fontSizeUnit == UNSPECIFIED) {
        fontSizeUnit = ancestor.fontSizeUnit;
        fontSize = ancestor.fontSize;
      }
      if (textEmphasis == null) {
        textEmphasis = ancestor.textEmphasis;
      }
      if (shearPercentage == UNSPECIFIED_SHEAR) {
        shearPercentage = ancestor.shearPercentage;
      }
      // attributes not inherited as of http://www.w3.org/TR/ttml1/
      if (chaining && !hasBackgroundColor && ancestor.hasBackgroundColor) {
        setBackgroundColor(ancestor.backgroundColor);
      }
      if (chaining && rubyType == UNSPECIFIED && ancestor.rubyType != UNSPECIFIED) {
        rubyType = ancestor.rubyType;
      }
    }
    return this;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setId(@Nullable String id) {
    this.id = id;
    return this;
  }

  @Nullable
  public String getId() {
    return id;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setRubyType(@RubyType int rubyType) {
    this.rubyType = rubyType;
    return this;
  }

  public @RubyType int getRubyType() {
    return rubyType;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setRubyPosition(@TextAnnotation.Position int position) {
    this.rubyPosition = position;
    return this;
  }

  public @TextAnnotation.Position int getRubyPosition() {
    return rubyPosition;
  }

  @Nullable
  public Layout.Alignment getTextAlign() {
    return textAlign;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setTextAlign(@Nullable Layout.Alignment textAlign) {
    this.textAlign = textAlign;
    return this;
  }

  @Nullable
  public Layout.Alignment getMultiRowAlign() {
    return multiRowAlign;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setMultiRowAlign(@Nullable Layout.Alignment multiRowAlign) {
    this.multiRowAlign = multiRowAlign;
    return this;
  }

  /** Returns true if the source entity has {@code tts:textCombine=all}. */
  public boolean getTextCombine() {
    return textCombine == ON;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setTextCombine(boolean combine) {
    this.textCombine = combine ? ON : OFF;
    return this;
  }

  @Nullable
  public TextEmphasis getTextEmphasis() {
    return textEmphasis;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setTextEmphasis(@Nullable TextEmphasis textEmphasis) {
    this.textEmphasis = textEmphasis;
    return this;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setFontSize(float fontSize) {
    this.fontSize = fontSize;
    return this;
  }

  @CanIgnoreReturnValue
  public TtmlStyle setFontSizeUnit(int fontSizeUnit) {
    this.fontSizeUnit = fontSizeUnit;
    return this;
  }

  public @FontSizeUnit int getFontSizeUnit() {
    return fontSizeUnit;
  }

  public float getFontSize() {
    return fontSize;
  }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.text.ttml;

import android.graphics.Typeface;
import android.text.Layout;
import org.telegram.messenger.exoplayer.util.Assertions;

/**
 * Style object of a <code>TtmlNode</code>
 */
/* package */ final class TtmlStyle {

  public static final int UNSPECIFIED = -1;

  public static final int STYLE_NORMAL = Typeface.NORMAL;
  public static final int STYLE_BOLD = Typeface.BOLD;
  public static final int STYLE_ITALIC = Typeface.ITALIC;
  public static final int STYLE_BOLD_ITALIC = Typeface.BOLD_ITALIC;

  public static final int FONT_SIZE_UNIT_PIXEL = 1;
  public static final int FONT_SIZE_UNIT_EM = 2;
  public static final int FONT_SIZE_UNIT_PERCENT = 3;

  private static final int OFF = 0;
  private static final int ON = 1;

  private String fontFamily;
  private int fontColor;
  private boolean hasFontColor;
  private int backgroundColor;
  private boolean hasBackgroundColor;
  private int linethrough;
  private int underline;
  private int bold;
  private int italic;
  private int fontSizeUnit;
  private float fontSize;
  private String id;
  private TtmlStyle inheritableStyle;
  private Layout.Alignment textAlign;

  public TtmlStyle() {
    linethrough = UNSPECIFIED;
    underline = UNSPECIFIED;
    bold = UNSPECIFIED;
    italic = UNSPECIFIED;
    fontSizeUnit = UNSPECIFIED;
  }

  /**
   * Returns the style or {@link #UNSPECIFIED} when no style information is given.
   *
   * @return {@link #UNSPECIFIED}, {@link #STYLE_NORMAL}, {@link #STYLE_BOLD}, {@link #STYLE_BOLD}
   *     or {@link #STYLE_BOLD_ITALIC}.
   */
  public int getStyle() {
    if (bold == UNSPECIFIED && italic == UNSPECIFIED) {
      return UNSPECIFIED;
    }
    return (bold != UNSPECIFIED ? bold : STYLE_NORMAL)
        | (italic != UNSPECIFIED ? italic : STYLE_NORMAL);
  }

  public boolean isLinethrough() {
    return linethrough == ON;
  }

  public TtmlStyle setLinethrough(boolean linethrough) {
    Assertions.checkState(inheritableStyle == null);
    this.linethrough = linethrough ? ON : OFF;
    return this;
  }

  public boolean isUnderline() {
    return underline == ON;
  }

  public TtmlStyle setUnderline(boolean underline) {
    Assertions.checkState(inheritableStyle == null);
    this.underline = underline ? ON : OFF;
    return this;
  }

  public String getFontFamily() {
    return fontFamily;
  }

  public TtmlStyle setFontFamily(String fontFamily) {
    Assertions.checkState(inheritableStyle == null);
    this.fontFamily = fontFamily;
    return this;
  }

  public int getFontColor() {
    if (!hasFontColor) {
      throw new IllegalStateException("Font color has not been defined.");
    }
    return fontColor;
  }

  public TtmlStyle setFontColor(int fontColor) {
    Assertions.checkState(inheritableStyle == null);
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

  public TtmlStyle setBackgroundColor(int backgroundColor) {
    this.backgroundColor = backgroundColor;
    hasBackgroundColor = true;
    return this;
  }

  public boolean hasBackgroundColor() {
    return hasBackgroundColor;
  }

  public TtmlStyle setBold(boolean isBold) {
    Assertions.checkState(inheritableStyle == null);
    bold = isBold ? STYLE_BOLD : STYLE_NORMAL;
    return this;
  }

  public TtmlStyle setItalic(boolean isItalic) {
    Assertions.checkState(inheritableStyle == null);
    italic = isItalic ? STYLE_ITALIC : STYLE_NORMAL;
    return this;
  }

  /**
   * Inherits from an ancestor style. Properties like <i>tts:backgroundColor</i> which
   * are not inheritable are not inherited as well as properties which are already set locally
   * are never overridden.
   *
   * @param ancestor the ancestor style to inherit from
   */
  public TtmlStyle inherit(TtmlStyle ancestor) {
    return inherit(ancestor, false);
  }

  /**
   * Chains this style to referential style. Local properties which are already set
   * are never overridden.
   *
   * @param ancestor the referential style to inherit from
   */
  public TtmlStyle chain(TtmlStyle ancestor) {
    return inherit(ancestor, true);
  }

  private TtmlStyle inherit(TtmlStyle ancestor, boolean chaining) {
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
      if (fontFamily == null) {
        fontFamily = ancestor.fontFamily;
      }
      if (linethrough == UNSPECIFIED) {
        linethrough = ancestor.linethrough;
      }
      if (underline == UNSPECIFIED) {
        underline = ancestor.underline;
      }
      if (textAlign == null) {
        textAlign = ancestor.textAlign;
      }
      if (fontSizeUnit == UNSPECIFIED) {
        fontSizeUnit = ancestor.fontSizeUnit;
        fontSize = ancestor.fontSize;
      }
      // attributes not inherited as of http://www.w3.org/TR/ttml1/
      if (chaining && !hasBackgroundColor && ancestor.hasBackgroundColor) {
        setBackgroundColor(ancestor.backgroundColor);
      }
    }
    return this;
  }

  public TtmlStyle setId(String id) {
    this.id = id;
    return this;
  }

  public String getId() {
    return id;
  }

  public Layout.Alignment getTextAlign() {
    return textAlign;
  }

  public TtmlStyle setTextAlign(Layout.Alignment textAlign) {
    this.textAlign = textAlign;
    return this;
  }

  public TtmlStyle setFontSize(float fontSize) {
    this.fontSize = fontSize;
    return this;
  }

  public TtmlStyle setFontSizeUnit(int fontSizeUnit) {
    this.fontSizeUnit = fontSizeUnit;
    return this;
  }

  public int getFontSizeUnit() {
    return fontSizeUnit;
  }

  public float getFontSize() {
    return fontSize;
  }

}

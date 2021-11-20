package org.telegram.ui;

import android.graphics.Typeface;

public class FontType {
    public String fontName;
    public String fontSampleText;
    public int fontIcon;
    public String fontPath = null;
    public Typeface font = null;

    public FontType(String fontName, String fontSampleText, int fontIcon, String fontPath, Typeface font) {
        this.fontName = fontName;
        this.fontSampleText = fontSampleText;
        this.fontIcon = fontIcon;
        this.fontPath = fontPath;
        this.font = font;
    }
}

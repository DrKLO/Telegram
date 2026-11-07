package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.util.AttributeSet;
import android.widget.TextView;

import org.telegram.messenger.SharedConfig;

public class PersianTextView extends TextView {

    private boolean fontReady;
    private boolean updatingFont;
    private boolean lastFontEnabled;
    private Typeface lastFontTypeface;
    private boolean lastFontFakeBold;
    private float lastFontSkew;
    private BufferType textBufferType;

    public PersianTextView(Context context) {
        super(context);
        initializeFont();
    }

    public PersianTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeFont();
    }

    public PersianTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeFont();
    }

    private void initializeFont() {
        fontReady = true;
        refreshFont();
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        textBufferType = type;
        if (fontReady && !updatingFont) {
            text = PersianFontApplier.applyForPaint(text, getPaint());
            rememberFontState();
        }
        super.setText(text, type);
    }

    @Override
    public void setTypeface(Typeface typeface) {
        super.setTypeface(typeface);
        refreshFont();
    }

    @Override
    public void setTypeface(Typeface typeface, int style) {
        boolean wasUpdating = updatingFont;
        updatingFont = true;
        try {
            super.setTypeface(typeface, style);
        } finally {
            updatingFont = wasUpdating;
        }
        refreshFont();
    }

    private void refreshFont() {
        // TextView invokes overridable methods before this constructor has finished.
        if (!fontReady || updatingFont) {
            return;
        }
        updatingFont = true;
        try {
            CharSequence text = getText();
            if (text instanceof Editable) {
                PersianFontApplier.applyToEditable((Editable) text, getPaint());
            } else {
                CharSequence styled = PersianFontApplier.applyForPaint(text, getPaint());
                if (styled != text) {
                    super.setText(styled, textBufferType != null ? textBufferType : BufferType.NORMAL);
                }
            }
            rememberFontState();
        } finally {
            updatingFont = false;
        }
    }

    private void rememberFontState() {
        lastFontEnabled = SharedConfig.useVazirmatnFont;
        lastFontTypeface = getTypeface();
        lastFontFakeBold = getPaint().isFakeBoldText();
        lastFontSkew = getPaint().getTextSkewX();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshFont();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (lastFontEnabled != SharedConfig.useVazirmatnFont
                || lastFontTypeface != getTypeface()
                || lastFontFakeBold != getPaint().isFakeBoldText()
                || lastFontSkew != getPaint().getTextSkewX()) {
            refreshFont();
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}

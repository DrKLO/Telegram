package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.PaintTypeface;
import org.telegram.ui.Components.Paint.Swatch;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Rect;

public class TextPaintView extends EntityView {

    private EditTextOutline editText;
    private Swatch swatch;
    private int currentType;
    private int baseFontSize;
    private int align;

    private PaintTypeface typeface = PaintTypeface.ROBOTO_MEDIUM;

    public TextPaintView(Context context, Point position, int fontSize, CharSequence text, Swatch swatch, int type) {
        super(context, position);

        baseFontSize = fontSize;

        editText = new EditTextOutline(context) {
            { animatedEmojiOffsetX = AndroidUtilities.dp(8); }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (selectionView == null || selectionView.getVisibility() != VISIBLE) {
                    return false;
                }
                return super.dispatchTouchEvent(event);
            }
        };
        editText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
        editText.setClickable(false);
        editText.setEnabled(false);
        editText.setCursorColor(0xffffffff);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseFontSize);
        editText.setCursorSize(AndroidUtilities.dp(baseFontSize * 0.4f));
        editText.setText(text);
        editText.setTextColor(swatch.color);
        editText.setTypeface(null, Typeface.BOLD);
        editText.setHorizontallyScrolling(false);
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setFocusableInTouchMode(true);
        editText.setInputType(editText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        addView(editText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        if (Build.VERSION.SDK_INT >= 23) {
            editText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        }

        setSwatch(swatch);
        setType(type);

        updatePosition();

        editText.addTextChangedListener(new TextWatcher() {
            private String text;
            private int beforeCursorPosition = 0;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                text = s.toString();
                beforeCursorPosition = start;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                editText.removeTextChangedListener(this);

                if (editText.getLineCount() > 9) {
                    editText.setText(text);
                    editText.setSelection(beforeCursorPosition);
                }
                
                editText.addTextChangedListener(this);
            }
        });
    }

    public TextPaintView(Context context, TextPaintView textPaintView, Point position) {
        this(context, position, textPaintView.baseFontSize, textPaintView.getText(), textPaintView.getSwatch(), textPaintView.currentType);
        setRotation(textPaintView.getRotation());
        setScale(textPaintView.getScale());
        setTypeface(textPaintView.getTypeface());
        setAlign(textPaintView.getAlign());

        int gravity;
        switch (getAlign()) {
            default:
            case PaintTextOptionsView.ALIGN_LEFT:
                gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                break;
            case PaintTextOptionsView.ALIGN_CENTER:
                gravity = Gravity.CENTER;
                break;
            case PaintTextOptionsView.ALIGN_RIGHT:
                gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                break;
        }

        editText.setGravity(gravity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            int textAlign;
            switch (getAlign()) {
                default:
                case PaintTextOptionsView.ALIGN_LEFT:
                    textAlign = LocaleController.isRTL ? View.TEXT_ALIGNMENT_TEXT_END : View.TEXT_ALIGNMENT_TEXT_START;
                    break;
                case PaintTextOptionsView.ALIGN_CENTER:
                    textAlign = View.TEXT_ALIGNMENT_CENTER;
                    break;
                case PaintTextOptionsView.ALIGN_RIGHT:
                    textAlign = LocaleController.isRTL ? View.TEXT_ALIGNMENT_TEXT_START : View.TEXT_ALIGNMENT_TEXT_END;
                    break;
            }
            editText.setTextAlignment(textAlign);
        }
    }

    public int getBaseFontSize() {
        return baseFontSize;
    }

    public void setBaseFontSize(int baseFontSize) {
        this.baseFontSize = baseFontSize;

        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseFontSize);
        editText.setCursorSize(AndroidUtilities.dp(baseFontSize * 0.4f));

        if (editText.getText() instanceof Spanned) {
            Spanned spanned = (Spanned) editText.getText();
            Emoji.EmojiSpan[] spans = spanned.getSpans(0, spanned.length(), Emoji.EmojiSpan.class);
            for (int i = 0; i < spans.length; ++i) {
                spans[i].replaceFontMetrics(getFontMetricsInt());
            }

            AnimatedEmojiSpan[] spans2 = spanned.getSpans(0, spanned.length(), AnimatedEmojiSpan.class);
            for (int i = 0; i < spans2.length; ++i) {
                spans2[i].replaceFontMetrics(getFontMetricsInt());
            }

            editText.invalidateForce();
        }
    }

    public void setAlign(int align) {
        this.align = align;
    }

    public int getAlign() {
        return align;
    }

    public void setTypeface(PaintTypeface typeface) {
        this.typeface = typeface;
        editText.setTypeface(typeface.getTypeface());
    }

    public void setTypeface(String key) {
        for (PaintTypeface typeface : PaintTypeface.get()) {
            if (typeface.getKey().equals(key)) {
                setTypeface(typeface);
                break;
            }
        }
    }

    public PaintTypeface getTypeface() {
        return typeface;
    }

    public EditTextOutline getEditText() {
        return editText;
    }

    public void setMaxWidth(int maxWidth) {
        editText.setMaxWidth(maxWidth);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updatePosition();
    }

    public CharSequence getText() {
        return editText.getText();
    }

    public void setText(CharSequence text) {
        editText.setText(text);
    }

    public Paint.FontMetricsInt getFontMetricsInt() {
        return editText.getPaint().getFontMetricsInt();
    }

    public float getFontSize() {
        return editText.getTextSize();
    }

    public View getFocusedView() {
        return editText;
    }

    public void beginEditing() {
        editText.setEnabled(true);
        editText.setClickable(true);
        editText.requestFocus();
        editText.setSelection(editText.getText().length());
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText), 300);
    }

    public void endEditing() {
        editText.clearFocus();
        editText.setEnabled(false);
        editText.setClickable(false);
        updateSelectionView();
    }

    public Swatch getSwatch() {
        return swatch;
    }

    public int getTextSize() {
        return (int) editText.getTextSize();
    }

    public void setSwatch(Swatch swatch) {
        this.swatch = swatch;
        updateColor();
    }

    public void setType(int type) {
        currentType = type;
        updateColor();
    }

    public int getType() {
        return currentType;
    }

    public void updateColor() {
        if (currentType == 0) {
            editText.setTextColor(0xffffffff);
            editText.setStrokeColor(swatch.color);
            editText.setFrameColor(0);
            editText.setShadowLayer(0, 0, 0, 0);
        } else if (currentType == 1) {
            editText.setTextColor(swatch.color);
            editText.setStrokeColor(0);
            editText.setFrameColor(0);
            editText.setShadowLayer(5, 0, 1, 0x66000000);
        } else if (currentType == 2) {
            editText.setTextColor(0xff000000);
            editText.setStrokeColor(0);
            editText.setFrameColor(swatch.color);
            editText.setShadowLayer(0, 0, 0, 0);
        }
    }

    @Override
    protected Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        if (parentView == null) {
            return new Rect();
        }
        float scale = parentView.getScaleX();
        float width = getMeasuredWidth() * getScale() + AndroidUtilities.dp(64) / scale;
        float height = getMeasuredHeight() * getScale() + AndroidUtilities.dp(52) / scale;
        return new Rect((getPositionX() - width / 2.0f) * scale, (getPositionY() - height / 2.0f) * scale, width * scale, height * scale);
    }

    protected TextViewSelectionView createSelectionView() {
        return new TextViewSelectionView(getContext());
    }

    public class TextViewSelectionView extends SelectionView {

        public TextViewSelectionView(Context context) {
            super(context);
        }

        @Override
        protected int pointInsideHandle(float x, float y) {
            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dp(19.5f);

            float inset = radius + thickness;
            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            float middle = inset + height / 2.0f;

            if (x > inset - radius && y > middle - radius && x < inset + radius && y < middle + radius) {
                return SELECTION_LEFT_HANDLE;
            } else if (x > inset + width - radius && y > middle - radius && x < inset + width + radius && y < middle + radius) {
                return SELECTION_RIGHT_HANDLE;
            }

            if (x > inset && x < width && y > inset && y < height) {
                return SELECTION_WHOLE_HANDLE;
            }

            return 0;
        }

        private Path path = new Path();

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float thickness = AndroidUtilities.dp(2.0f);
            float radius = AndroidUtilities.dp(4.5f);

            float inset = radius + thickness + AndroidUtilities.dp(15);

            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            AndroidUtilities.rectTmp.set(inset, inset, inset + width, inset + height);

            float R = AndroidUtilities.dp(12);
            float rx = Math.min(R, width / 2f), ry = Math.min(R, height / 2f);

            path.rewind();
            AndroidUtilities.rectTmp.set(inset, inset, inset + rx * 2, inset + ry * 2);
            path.arcTo(AndroidUtilities.rectTmp, 180, 90);
            AndroidUtilities.rectTmp.set(inset + width - rx * 2, inset, inset + width, inset + ry * 2);
            path.arcTo(AndroidUtilities.rectTmp, 270, 90);
            canvas.drawPath(path, paint);

            path.rewind();
            AndroidUtilities.rectTmp.set(inset, inset + height - ry * 2, inset + rx * 2, inset + height);
            path.arcTo(AndroidUtilities.rectTmp, 180, -90);
            AndroidUtilities.rectTmp.set(inset + width - rx * 2, inset + height - ry * 2, inset + width, inset + height);
            path.arcTo(AndroidUtilities.rectTmp, 90, -90);
            canvas.drawPath(path, paint);

            canvas.drawLine(inset, inset + ry, inset, inset + height - ry, paint);
            canvas.drawLine(inset + width, inset + ry, inset + width, inset + height - ry, paint);

            canvas.drawCircle(inset, inset + height / 2.0f, radius, dotPaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius, dotStrokePaint);

            canvas.drawCircle(inset + width, inset + height / 2.0f, radius, dotPaint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius, dotStrokePaint);
        }
    }
}

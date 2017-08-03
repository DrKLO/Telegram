package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Swatch;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Rect;

public class TextPaintView extends EntityView {

    private EditTextOutline editText;
    private Swatch swatch;
    private boolean stroke;
    private int baseFontSize;

    public TextPaintView(Context context, Point position, int fontSize, String text, Swatch swatch, boolean stroke) {
        super(context, position);

        baseFontSize = fontSize;

        editText = new EditTextOutline(context);
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
        editText.setClickable(false);
        editText.setEnabled(false);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseFontSize);
        editText.setText(text);
        editText.setTextColor(swatch.color);
        editText.setTypeface(null, Typeface.BOLD);
        editText.setGravity(Gravity.CENTER);
        editText.setHorizontallyScrolling(false);
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setFocusableInTouchMode(true);
        editText.setInputType(editText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        addView(editText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        if (Build.VERSION.SDK_INT >= 23) {
            editText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        }

        setSwatch(swatch);
        setStroke(stroke);

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
        this(context, position, textPaintView.baseFontSize, textPaintView.getText(), textPaintView.getSwatch(), textPaintView.stroke);
        setRotation(textPaintView.getRotation());
        setScale(textPaintView.getScale());
    }

    public void setMaxWidth(int maxWidth) {
        editText.setMaxWidth(maxWidth);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updatePosition();
    }

    public String getText() {
        return editText.getText().toString();
    }

    public void setText(String text) {
        editText.setText(text);
    }

    public View getFocusedView() {
        return editText;
    }

    public void beginEditing() {
        editText.setEnabled(true);
        editText.setClickable(true);
        editText.requestFocus();
        editText.setSelection(editText.getText().length());
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

    public void setSwatch(Swatch swatch) {
        this.swatch = swatch;
        updateColor();
    }

    public void setStroke(boolean stroke) {
        this.stroke = stroke;
        updateColor();
    }

    private void updateColor() {
        if (stroke) {
            editText.setTextColor(0xffffffff);
            editText.setStrokeColor(swatch.color);
            editText.setShadowLayer(0, 0, 0, 0);
        } else {
            editText.setTextColor(swatch.color);
            editText.setStrokeColor(Color.TRANSPARENT);
            editText.setShadowLayer(8, 0, 2, 0xaa000000);
        }
    }

    @Override
    protected Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        float scale = parentView.getScaleX();
        float width = getWidth() * (getScale()) + AndroidUtilities.dp(46) / scale;
        float height = getHeight() * (getScale()) + AndroidUtilities.dp(20) / scale;
        return new Rect((position.x - width / 2.0f) * scale, (position.y - height / 2.0f) * scale, width * scale, height * scale);
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
            float width = getWidth() - inset * 2;
            float height = getHeight() - inset * 2;

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

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float space = AndroidUtilities.dp(3.0f);
            float length = AndroidUtilities.dp(3.0f);
            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dp(4.5f);

            float inset = radius + thickness + AndroidUtilities.dp(15);

            float width = getWidth() - inset * 2;
            float height = getHeight() - inset * 2;

            int xCount = (int) (Math.floor(width / (space + length)));
            float xGap = (float) Math.ceil(((width - xCount * (space + length)) + space) / 2.0f);

            for (int i = 0; i < xCount; i++) {
                float x = xGap + inset + i * (length + space);
                canvas.drawRect(x, inset - thickness / 2.0f, x + length, inset + thickness / 2.0f, paint);
                canvas.drawRect(x, inset + height - thickness / 2.0f, x + length, inset + height + thickness / 2.0f, paint);
            }

            int yCount = (int) (Math.floor(height / (space + length)));
            float yGap = (float) Math.ceil(((height - yCount * (space + length)) + space) / 2.0f);

            for (int i = 0; i < yCount; i++) {
                float y = yGap + inset + i * (length + space);
                canvas.drawRect(inset - thickness / 2.0f, y, inset + thickness / 2.0f, y + length, paint);
                canvas.drawRect(inset + width - thickness / 2.0f, y, inset + width + thickness / 2.0f, y + length, paint);
            }

            canvas.drawCircle(inset, inset + height / 2.0f, radius, dotPaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius, dotStrokePaint);

            canvas.drawCircle(inset + width, inset + height / 2.0f, radius, dotPaint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius, dotStrokePaint);
        }
    }
}

package org.telegram.ui.Animations;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

// TODO agolokoz: s10 left right sides
public class SelectColorBottomSheet extends BottomSheet {

    private final ColorSelectView colorSelectView = new ColorSelectView(getContext(), new ColorListener() {
        @Override
        public void onColorChanged(int color, @Nullable Object tag) {
            colorSliderView.setColors(color, Color.BLACK);
        }
    });

    private final ColorSliderView colorSliderView = new ColorSliderView(getContext(), new ColorListener() {
        @Override
        public void onColorChanged(int color, @Nullable Object tag) {
            selectedColor = color;
            if (colorListener != null) {
                colorListener.onColorChanged(color, null);
            }
        }
    });

    @Nullable
    private ColorListener colorListener;
    @ColorInt
    private int selectedColor;

    public SelectColorBottomSheet(Context context, boolean needFocus) {
        super(context, needFocus);
        setCanDismissWithSwipe(false);
        setDimBehind(false);
        setSelectedColor(Color.RED);
        colorSliderView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int shadowHeightDp = 3;
        int shadowRes = R.drawable.header_shadow;

        LinearLayout linearLayout = new LinearLayout(getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        View shadowView1 = new View(getContext());
        shadowView1.setBackgroundResource(shadowRes);
        shadowView1.setRotation(180f);
        linearLayout.addView(shadowView1, MATCH_PARENT, LayoutHelper.createFrame(MATCH_PARENT, shadowHeightDp));

        linearLayout.addView(colorSelectView, MATCH_PARENT, WRAP_CONTENT);

        View dividerView = new View(getContext());
        dividerView.setBackgroundColor(Theme.dividerPaint.getColor());
        linearLayout.addView(dividerView, MATCH_PARENT, AndroidUtilities.dp(0.5f));

        linearLayout.addView(colorSliderView, MATCH_PARENT, WRAP_CONTENT);

        FrameLayout buttonsLayout = new FrameLayout(getContext());
        buttonsLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        {
            View shadowView2 = new View(getContext());
            shadowView2.setAlpha(0.75f);
            shadowView2.setBackgroundResource(shadowRes);
            shadowView2.setRotation(180f);
            buttonsLayout.addView(shadowView2, LayoutHelper.createFrame(MATCH_PARENT, shadowHeightDp, Gravity.START | Gravity.TOP, 0, 0, 0, shadowHeightDp));

            TextView cancelBtn = createButton(LocaleController.getString("", R.string.AnimationSettingsCancel));
            cancelBtn.setOnClickListener(v -> {
                if (colorListener != null) {
                    colorListener.onColorCancelled(null);
                }
                dismiss();
            });
            buttonsLayout.addView(cancelBtn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 0, shadowHeightDp, 0, 0));

            TextView applyBtn = createButton(LocaleController.getString("", R.string.AnimationSettingsApply));
            applyBtn.setOnClickListener(v -> {
                if (colorListener != null) {
                    colorListener.onColorApplied(selectedColor, null);
                }
                dismiss();
            });
            buttonsLayout.addView(applyBtn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, 0, shadowHeightDp, 0, 0));
        }

        linearLayout.addView(buttonsLayout, MATCH_PARENT, WRAP_CONTENT);

        containerView = linearLayout;
    }

    public void setSelectedColor(int selectedColor) {
        this.selectedColor = selectedColor;

        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(selectedColor), Color.green(selectedColor), Color.blue(selectedColor), hsv);
        float value = hsv[2];
        hsv[2] = 1f;
        int hsColor = Color.HSVToColor(hsv);
        hsv[2] = value;

        colorSelectView.setColor(hsColor);
        colorSliderView.setColors(hsColor, Color.BLACK);
        colorSliderView.setValue(value);
    }

    public void setColorListener(@Nullable ColorListener colorListener) {
        this.colorListener = colorListener;
    }

    public int getSelectedColor() {
        return selectedColor;
    }

    @Override
    public void dismiss() {
        if (colorListener != null) {
            colorListener.onColorCancelled(null);
        }
        super.dismiss();
    }

    private TextView createButton(String text) {
        TextView button = new TextView(getContext());
        button.setAllCaps(true);
        int color = Theme.getColor(Theme.key_listSelector);
        Drawable backgroundDrawable = Theme.createSimpleSelectorRoundRectDrawable(0, 0, color, 0xff000000);
        button.setBackground(backgroundDrawable);
        int lrPadding = AndroidUtilities.dp(21);
        int tbPadding = AndroidUtilities.dp(18);
        button.setPadding(lrPadding, tbPadding, lrPadding, tbPadding);
        button.setText(text);
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        return button;
    }


    private static class ColorSelectView extends View {

        private final Paint backgroundGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] hsv = new float[3];
        private final CursorDrawable cursorDrawable;
        private final ColorListener colorListener;

        private Bitmap backgroundBitmap;
        private float xCursor = -1;
        private float yCursor = -1;
        private int prevColor = 0;
        private int color = 0;

        public ColorSelectView(Context context, ColorListener colorListener) {
            super(context);
            this.colorListener = colorListener;
            cursorDrawable = new CursorDrawable(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = Math.min(AndroidUtilities.dp(163), AndroidUtilities.displaySize.y / 2);
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            if (w == oldw && h == oldh) {
                return;
            }
            backgroundBitmap = createGradientBitmap(w, h);
            updateCursorByColor();
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getPointerCount() > 1) {
                return false;
            }

            final float x = event.getX();
            final float y = event.getY();
            final int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    xCursor = MathUtils.clamp(x, 0f, getWidth() - 1);
                    yCursor = MathUtils.clamp(y, 0f, getHeight() - 1);
                    int color = getColor((int) xCursor, (int) yCursor);
                    if (prevColor != color) {
                        cursorDrawable.setColor(color);
                        if (colorListener != null) {
                            colorListener.onColorChanged(color, null);
                        }
                        prevColor = color;
                        invalidate();
                    }
                    break;
            }

            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawBitmap(backgroundBitmap, 0f, 0f, null);
            if (xCursor != -1 || yCursor != -1) {
                canvas.save();
                canvas.translate(xCursor - cursorDrawable.getBounds().width() * 0.5f, yCursor - cursorDrawable.getBounds().height() * 0.5f);
                cursorDrawable.draw(canvas);
                canvas.restore();
            }
        }

        public void setColor(int color) {
            this.color = color;
            updateCursorByColor();
        }

        private Bitmap createGradientBitmap(int width, int height) {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            int[] backgroundColors = new int[] { Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED };
            LinearGradient backgroundGradient = new LinearGradient(0f, 0f, width, 0, backgroundColors, null, Shader.TileMode.CLAMP);
            int[] foregroundColors = new int[] { Color.TRANSPARENT, Color.WHITE };
            LinearGradient foregroundGradient = new LinearGradient(0f, 0, 0, height, foregroundColors, null, Shader.TileMode.CLAMP);
            ComposeShader shader = new ComposeShader(backgroundGradient, foregroundGradient, PorterDuff.Mode.SRC_OVER);
            backgroundGradientPaint.setShader(shader);

            canvas.drawRect(0f, 0f, width, height, backgroundGradientPaint);
            return bitmap;
        }

        @ColorInt
        private int getColor(int x, int y) {
            if (getWidth() == 0 || getHeight() == 0) {
                return Color.TRANSPARENT;
            }
            hsv[0] = x * 360f / getWidth();
            hsv[1] = 1f - y * 1f / getHeight();
            return Color.HSVToColor(hsv);
        }

        private void updateCursorByColor() {
            if (getWidth() == 0 || getHeight() == 0 || color == 0) {
                return;
            }

            Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv);
            xCursor = getWidth() * hsv[0] / 360f;
            yCursor = getHeight() * (1f - hsv[1]);

            float vTemp = hsv[2];
            hsv[2] = 1f;
            int color = Color.HSVToColor(hsv);
            hsv[2] = vTemp;
            cursorDrawable.setColor(color);

            invalidate();
        }
    }


    private static class ColorSliderView extends View {

        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF gradientRect = new RectF();
        private final int leftRightSpace = AndroidUtilities.dp(20);
        private final int gradientHeight = AndroidUtilities.dp(7);
        private final float[] hsv = new float[]{ 0f, 0f, 1f };
        private final ColorListener colorListener;
        private final CursorDrawable cursorDrawable;

        private int color1 = Color.WHITE;
        private int color2 = Color.BLACK;
        private float xCursor = leftRightSpace;
        private int prevColor;

        public ColorSliderView(Context context, ColorListener colorListener) {
            super(context);
            this.colorListener = colorListener;
            this.cursorDrawable = new CursorDrawable(context);

            int size = AndroidUtilities.dp(21);
            cursorDrawable.setBounds(0, 0, size, size);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(52));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            if (w == oldw && h == oldh) {
                return;
            }
            setColors(color1, color2);
            updateGradient();
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getPointerCount() > 1) {
                return false;
            }

            final float x = event.getX();
            final int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    xCursor = MathUtils.clamp(x, leftRightSpace, getWidth() - leftRightSpace);
                    hsv[2] = 1f - (x - leftRightSpace) / (getWidth() - leftRightSpace * 2);
                    onHsvChanged();
                    invalidate();
                    break;
            }

            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRoundRect(gradientRect, gradientHeight * 0.5f, gradientHeight * 0.5f, gradientPaint);
            if (xCursor > -1f) {
                canvas.save();
                canvas.translate(xCursor - cursorDrawable.getBounds().width() * 0.5f, (getHeight() - cursorDrawable.getBounds().height()) * 0.5f);
                cursorDrawable.draw(canvas);
                canvas.restore();
            }
        }

        public void setColors(int color1, int color2) {
            this.color1 = color1;
            this.color2 = color2;

            float vTemp = hsv[2];
            Color.RGBToHSV(Color.red(color1), Color.green(color1), Color.blue(color1), hsv);
            hsv[2] = vTemp;
            setValue(vTemp);

            updateGradient();
            invalidate();
        }

        public void setValue(float value) {
            xCursor = leftRightSpace + (getWidth() - leftRightSpace * 2) * (1f - value);
            hsv[2] = MathUtils.clamp(value, 0f, 1f);
            onHsvChanged();
        }

        private void onHsvChanged() {
            int color = Color.HSVToColor(hsv);
            if (prevColor != color) {
                cursorDrawable.setColor(color);
                if (colorListener != null) {
                    colorListener.onColorChanged(color, null);
                }
                prevColor = color;
                invalidate();
            }
        }

        private void updateGradient() {
            if (getWidth() == 0) {
                return;
            }
            LinearGradient gradient = new LinearGradient(0f, 0f, getWidth(), 0f, color1, color2, Shader.TileMode.CLAMP);
            gradientPaint.setShader(gradient);

            float top = (getHeight() - gradientHeight) * 0.5f;
            gradientRect.set(leftRightSpace, top, getWidth() - leftRightSpace, top + gradientHeight);
        }
    }


    private static class CursorDrawable extends Drawable {

        private static final int defaultSize = AndroidUtilities.dp(32);

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint foregroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable shadowDrawable;

        private float innerRadius;
        private float outerRadius;

        public CursorDrawable(Context context) {
            backgroundPaint.setColor(Color.WHITE);
            shadowDrawable = ContextCompat.getDrawable(context, R.drawable.knob_shadow);
            setBounds(0, 0, getIntrinsicWidth(), getIntrinsicHeight());
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            shadowDrawable.draw(canvas);
            canvas.drawCircle(getBounds().width() * 0.5f, getBounds().height() * 0.5f, outerRadius, backgroundPaint);
            canvas.drawCircle(getBounds().width() * 0.5f, getBounds().height() * 0.5f, innerRadius, foregroundPaint);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            shadowDrawable.setBounds(bounds);
            outerRadius = bounds.width() * 0.5f - AndroidUtilities.dp(1.5f);
            innerRadius = outerRadius - AndroidUtilities.dp(2);
        }

        @Override
        public void setAlpha(int alpha) {
            shadowDrawable.setAlpha(alpha);
            backgroundPaint.setAlpha(alpha);
            foregroundPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            shadowDrawable.setColorFilter(colorFilter);
            backgroundPaint.setColorFilter(colorFilter);
            foregroundPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return defaultSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return defaultSize;
        }

        public void setColor(@ColorInt int color) {
            foregroundPaint.setColor(color);
        }
    }

    public interface ColorListener {

        default void onColorChanged(@ColorInt int color, @Nullable Object tag) {}

        default void onColorApplied(@ColorInt int color, @Nullable Object tag) {}

        default void onColorCancelled(@Nullable Object tag) {}
    }
}

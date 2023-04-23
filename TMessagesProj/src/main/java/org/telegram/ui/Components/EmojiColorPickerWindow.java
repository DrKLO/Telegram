package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.PopupWindow;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CompoundEmoji;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.lang.reflect.Field;

public class EmojiColorPickerWindow extends PopupWindow {

    private ViewTreeObserver.OnScrollChangedListener mSuperScrollListener;
    private ViewTreeObserver mViewTreeObserver;

    private static Field superListenerField;
    private static final ViewTreeObserver.OnScrollChangedListener NOP = () -> {};

    public EmojiColorPickerView pickerView;
    private boolean isCompound;

    public static EmojiColorPickerWindow create(Context context, Theme.ResourcesProvider resourcesProvider) {
        EmojiColorPickerView view = new EmojiColorPickerView(context, resourcesProvider);
        EmojiColorPickerWindow window = new EmojiColorPickerWindow(view);
        window.init();
        return window;
    }

    private EmojiColorPickerWindow(EmojiColorPickerView view) {
        super(view);
        this.pickerView = view;

        setOutsideTouchable(true);
        setClippingEnabled(true);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        pickerView.setFocusableInTouchMode(true);
        pickerView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && isShowing()) {
                dismiss();
                return true;
            }
            return false;
        });
    }

    private final int emojiSize = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 40 : 32);

    public int getPopupWidth() {
        return emojiSize * 6 + AndroidUtilities.dp( 10 + 4 * 5 + (isCompound ? 3 : 0));
    }

    public int getPopupHeight() {
        return AndroidUtilities.dp(isCompound ? 11.66f : 15) + (isCompound ? 2 : 1) * emojiSize;
    }

    public int getSelection() {
        return pickerView.getSelection(0);
    }

    public String getSkinTone(int side) {
        int value = pickerView.getSelection(side);
        if (value < 1 || value > 5) {
            return null;
        }
        return CompoundEmoji.skinTones.get(value - 1);
    }

    public void setSelection(int value) {
        pickerView.setSelection(0, value);
    }

    public void onTouchMove(int x) {
        if (isCompound) {
            return;
        }
        int newSelection = Math.max(0, Math.min(5, x / (emojiSize + dp(4))));
        if (getSelection() != newSelection) {
            try {
                pickerView.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            } catch (Exception ignore) {}
            setSelection(newSelection);
        }
    }

    public boolean isCompound() {
        return isCompound;
    }

    public void setupArrow(int arrowX) {
        pickerView.setArrowX(arrowX);
    }

    public void setEmoji(String emoji) {
        isCompound = CompoundEmoji.getCompoundEmojiDrawable(emoji) != null;
        pickerView.setEmoji(isCompound, emoji);
        setWidth(getPopupWidth());
        setHeight(getPopupHeight());
    }

    public void updateColors() {
        pickerView.updateColors();
    }

    public void setOnSelectionUpdateListener(Utilities.Callback2<Integer, Integer> onSelectionUpdateListener) {
        pickerView.setOnSelectionUpdateListener(onSelectionUpdateListener);
    }

    public static class EmojiColorPickerView extends View {

        private final int emojiSize = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 40 : 32);
        private Drawable[] drawables = new Drawable[11];
        private Drawable backgroundDrawable;
        private Drawable arrowDrawable;
        private String currentEmoji;
        private boolean isCompound;
        private int arrowX;
        private int[] selection = new int[] { 0, 0 };
        private int[] lastSelection = new int[] { 0, 0 };
        private Paint rectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();
        private AnimatedFloat selection1Animated = new AnimatedFloat(this, 125, CubicBezierInterpolator.EASE_OUT_QUINT);
        private AnimatedFloat selection2Animated = new AnimatedFloat(this, 125, CubicBezierInterpolator.EASE_OUT_QUINT);

        private Theme.ResourcesProvider resourcesProvider;

        private Utilities.Callback2<Integer, Integer> onSelectionUpdate;

        public void setOnSelectionUpdateListener(Utilities.Callback2<Integer, Integer> onSelectionUpdateListener) {
            this.onSelectionUpdate = onSelectionUpdateListener;
        }

        public EmojiColorPickerView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;

            backgroundDrawable = getResources().getDrawable(R.drawable.stickers_back_all);
            arrowDrawable = getResources().getDrawable(R.drawable.stickers_back_arrow);
            updateColors();
        }

        public void updateColors() {
            Theme.setDrawableColor(backgroundDrawable, Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            Theme.setDrawableColor(arrowDrawable, Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            CompoundEmoji.setPlaceholderColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));
        }

        public void setArrowX(int arrowX) {
            this.arrowX = arrowX;
            invalidate();
        }

        public void setEmoji(boolean compound, String emoji) {
            isCompound = compound;
            currentEmoji = emoji;
            if (compound) {
                drawables[0] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, -1, -1);

                drawables[1] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, 0, -2);
                drawables[2] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, 1, -2);
                drawables[3] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, 2, -2);
                drawables[4] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, 3, -2);
                drawables[5] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, 4, -2);

                drawables[6] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, -2, 0);
                drawables[7] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, -2, 1);
                drawables[8] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, -2, 2);
                drawables[9] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, -2, 3);
                drawables[10] = CompoundEmoji.getCompoundEmojiDrawable(currentEmoji, -2, 4);
                
                Pair<Integer, Integer> pair = CompoundEmoji.isHandshake(emoji);
                if (pair != null) {
                    setSelection(0, pair.first);
                    setSelection(1, pair.second);
                    both = selection[0] == selection[1];
                }

                ignore = true;
            } else {
                for (int i = 0; i < 6; ++i) {
                    String coloredCode = emoji;
                    if (i != 0) {
                        String color = CompoundEmoji.skinTones.get(i - 1);
                        coloredCode = EmojiView.addColorToCode(emoji, color);
                    }
                    drawables[i] = Emoji.getEmojiBigDrawable(coloredCode);
                }
            }
            invalidate();
        }

        public String getEmoji() {
            return currentEmoji;
        }

        public void setSelection(int side, int position) {
            if (selection[side] == position) {
                return;
            }
            selection[side] = position;
            invalidate();
        }

        public int getSelection(int side) {
            return selection[side];
        }

        private int touchY = -1;
        private boolean both = true;
        private long downStart;
        private boolean ignore;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (ignore) {
                ignore = false;
                return false;
            }
            if (!isCompound) {
                return super.onTouchEvent(event);
            }

            int index = -1;
            for (int i = 0; i < drawables.length; ++i) {
                if (
                    drawables[i].getBounds().contains((int) event.getX(), (int) event.getY()) ||
                    touchY != -1 && (i == 0 || (
                        touchY == 0 && i >= 1 && i <= 5 ||
                        touchY == 1 && i >= 6 && i <= 10
                    )) && (
                        (int) event.getX() >= drawables[i].getBounds().left &&
                        (int) event.getX() <= drawables[i].getBounds().right
                    )
                ) {
                    index = i;
                    break;
                }
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchY = -1;
                    downStart = System.currentTimeMillis();
                    both = selection[0] == selection[1];
                }
                lastSelection[0] = selection[0];
                lastSelection[1] = selection[1];

                boolean dragging = System.currentTimeMillis() - downStart > 300 && event.getAction() == MotionEvent.ACTION_MOVE;
                if (index == 0) {
                    selection[0] = -1;
                    selection[1] = -1;
                } else if (index >= 1 && index <= 5 && (touchY == -1 || touchY == 0)) {
                    touchY = 0;
                    selection[0] = index - 1;
                    if (selection[1] == -1 || both && dragging) {
                        selection[1] = selection[0];
                    }
                } else if (index >= 6 && index <= 10 && (touchY == -1 || touchY == 1)) {
                    touchY = 1;
                    selection[1] = index - 6;
                    if (selection[0] == -1 || both && dragging) {
                        selection[0] = selection[1];
                    }
                }
                if (lastSelection[0] != selection[0] || lastSelection[1] != selection[1]) {
                    try {
                        try {
                            performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignore) {}
                    } catch (Exception ignore) {}
                    if (onSelectionUpdate != null) {
                        onSelectionUpdate.run(selection[0], selection[1]);
                    }
                }
                invalidate();
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    touchY = -1;
                }
                return true;
            }

            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(2));
            backgroundDrawable.draw(canvas);

            arrowDrawable.setBounds(arrowX - AndroidUtilities.dp(9), getMeasuredHeight() - AndroidUtilities.dp(6.34f), arrowX + AndroidUtilities.dp(9), getMeasuredHeight());
            arrowDrawable.draw(canvas);

            int x, y;

            if (currentEmoji != null) {
                if (isCompound) {
                    for (int iy = 0; iy < 2; ++iy) {
                        float select = (iy == 0 ? selection1Animated : selection2Animated).set(selection[iy]);
                        x = (int) (emojiSize * (1 + select) + AndroidUtilities.dp(5 + 3 * Math.max(0, Math.min(1, 1 + select)) + 4 * (1 + select)));
                        float mi = Math.max(0, Math.min(1, -select));
                        y = AndroidUtilities.lerp(
                            AndroidUtilities.dp(3) + iy * (emojiSize + AndroidUtilities.dp(1)),
                            (getMeasuredHeight() - emojiSize) / 2,
                            mi
                        );
                        rect.set(x, y, x + emojiSize, y + emojiSize);
                        rect.inset(AndroidUtilities.dp(-2), AndroidUtilities.dp(-2 * mi));
                        rectPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_listSelector, resourcesProvider), AndroidUtilities.lerp(1, 0.5f, mi)));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), rectPaint);

                        for (int ix = 0; ix < 5; ++ix) {
                            int i = 1 + ix + iy * 5;
                            x = emojiSize * (1 + ix) + AndroidUtilities.dp(8 + 4 * (1 + ix));
                            y = AndroidUtilities.dp(3) + (emojiSize + AndroidUtilities.dp(1)) * iy;
                            drawables[i].setBounds(x, y, x + emojiSize, y + emojiSize);
                            drawables[i].draw(canvas);
                        }
                    }
                    drawables[0].setBounds(
                        AndroidUtilities.dp(5),
                        (getMeasuredHeight() - emojiSize) / 2,
                        AndroidUtilities.dp(5) + emojiSize,
                        (getMeasuredHeight() + emojiSize) / 2
                    );
                    drawables[0].draw(canvas);
                    canvas.drawRect(
                        AndroidUtilities.dp(8.45f) + emojiSize,
                        AndroidUtilities.dp(2),
                        AndroidUtilities.dp(8.45f) + emojiSize + 1,
                        getMeasuredHeight() - AndroidUtilities.dp(6),
                        Theme.dividerPaint
                    );
                } else {
                    float select = selection1Animated.set(selection[0]);
                    x = (int) (emojiSize * select + AndroidUtilities.dp(5 + 4 * select));
                    y = AndroidUtilities.dp(5);
                    rect.set(x, y, x + emojiSize, y + emojiSize);
                    rect.inset(AndroidUtilities.dp(-2), AndroidUtilities.dp(-2));
                    rectPaint.setColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), rectPaint);

                    for (int a = 0; a < 6; a++) {
                        Drawable drawable = drawables[a];
                        if (drawable != null) {
                            x = emojiSize * a + AndroidUtilities.dp(5 + 4 * a);
                            float scale = .9f + .1f * (1f - Math.min(.5f, Math.abs(a - select)) * 2f);
                            canvas.save();
                            canvas.scale(scale, scale, x + emojiSize / 2f, y + emojiSize / 2f);
                            drawable.setBounds((int) x, (int) y, (int) x + emojiSize, (int) y + emojiSize);
                            drawable.draw(canvas);
                            canvas.restore();
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private void init() {
        if (superListenerField == null) {
            Field f = null;
            try {
                f = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
                f.setAccessible(true);
            } catch (Exception ignore) {}
            superListenerField = f;
        }
        if (superListenerField != null) {
            try {
                mSuperScrollListener = (ViewTreeObserver.OnScrollChangedListener) superListenerField.get(this);
                superListenerField.set(this, NOP);
            } catch (Exception e) {
                mSuperScrollListener = null;
            }
        }
    }

    private void unregisterListener() {
        if (mSuperScrollListener != null && mViewTreeObserver != null) {
            if (mViewTreeObserver.isAlive()) {
                mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
            }
            mViewTreeObserver = null;
        }
    }

    private void registerListener(View anchor) {
        if (mSuperScrollListener != null) {
            ViewTreeObserver vto = (anchor.getWindowToken() != null) ? anchor.getViewTreeObserver() : null;
            if (vto != mViewTreeObserver) {
                if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
                    mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                }
                if ((mViewTreeObserver = vto) != null) {
                    vto.addOnScrollChangedListener(mSuperScrollListener);
                }
            }
        }
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        try {
            super.showAsDropDown(anchor, xoff, yoff);
            registerListener(anchor);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void update(View anchor, int xoff, int yoff, int width, int height) {
        super.update(anchor, xoff, yoff, width, height);
        registerListener(anchor);
    }

    @Override
    public void update(View anchor, int width, int height) {
        super.update(anchor, width, height);
        registerListener(anchor);
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, x, y);
        unregisterListener();
    }

    @Override
    public void dismiss() {
        setFocusable(false);
        try {
            super.dismiss();
        } catch (Exception ignore) {}
        unregisterListener();
    }
}

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextPaint;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;
import androidx.core.view.GestureDetectorCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class CustomPhoneKeyboardView extends ViewGroup {
    public final static int KEYBOARD_HEIGHT_DP = 230;

    private final static int SIDE_PADDING = 10, BUTTON_PADDING = 6;

    private ImageView backButton;
    private EditText editText;
    private View[] views = new View[12];

    private View viewToFindFocus;

    private boolean dispatchBackWhenEmpty;
    private boolean runningLongClick;
    private Runnable onBackButton = () -> {
        checkFindEditText();
        if (editText == null || editText.length() == 0 && !dispatchBackWhenEmpty) return;

        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        playSoundEffect(SoundEffectConstants.CLICK);
        editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));

        if (runningLongClick) {
            postDelayed(this.onBackButton, 50);
        }
    };

    private boolean postedLongClick;
    private Runnable detectLongClick = () -> {
        postedLongClick = false;
        runningLongClick = true;
        onBackButton.run();
    };

    public CustomPhoneKeyboardView(Context context) {
        super(context);

        for (int i = 0; i < 11; i++) {
            if (i == 9) continue;

            String symbols;
            switch (i) {
                default:
                case 0:
                    symbols = "";
                    break;
                case 1:
                    symbols = "ABC";
                    break;
                case 2:
                    symbols = "DEF";
                    break;
                case 3:
                    symbols = "GHI";
                    break;
                case 4:
                    symbols = "JKL";
                    break;
                case 5:
                    symbols = "MNO";
                    break;
                case 6:
                    symbols = "PQRS";
                    break;
                case 7:
                    symbols = "TUV";
                    break;
                case 8:
                    symbols = "WXYZ";
                    break;
                case 10:
                    symbols = "+";
                    break;
            }
            String num = String.valueOf(i != 10 ? i + 1 : 0);
            views[i] = new NumberButtonView(context, num, symbols);
            views[i].setOnClickListener(v -> {
                checkFindEditText();
                if (editText == null) return;

                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                if (editText instanceof EditTextBoldCursor) {
                    ((EditTextBoldCursor) editText).setTextWatchersSuppressed(true, false);
                }

                Editable text = editText.getText();
                int newSelection = editText.getSelectionEnd() == editText.length() ? -1 : editText.getSelectionStart() + num.length();
                if (editText.getSelectionStart() != -1 && editText.getSelectionEnd() != -1) {
                    editText.setText(text.replace(editText.getSelectionStart(), editText.getSelectionEnd(), num));
                    editText.setSelection(newSelection == -1 ? editText.length() : newSelection);
                } else {
                    editText.setText(num);
                    editText.setSelection(editText.length());
                }

                if (editText instanceof EditTextBoldCursor) {
                    ((EditTextBoldCursor) editText).setTextWatchersSuppressed(false, true);
                }
            });
            addView(views[i]);
        }

        GestureDetectorCompat backDetector = setupBackButtonDetector(context);
        backButton = new ImageView(context) {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (postedLongClick || runningLongClick) {
                        postedLongClick = false;
                        runningLongClick = false;
                        removeCallbacks(detectLongClick);
                        removeCallbacks(onBackButton);
                    }
                }
                super.onTouchEvent(event);
                return backDetector.onTouchEvent(event);
            }
        };
        backButton.setImageResource(R.drawable.msg_clear_input);
        backButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        backButton.setBackground(getButtonDrawable());
        int pad = AndroidUtilities.dp(11);
        backButton.setPadding(pad, pad, pad, pad);
        backButton.setOnClickListener(v -> {});
        addView(views[11] = backButton);
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        return true;
    }

    public void setDispatchBackWhenEmpty(boolean dispatchBackWhenEmpty) {
        this.dispatchBackWhenEmpty = dispatchBackWhenEmpty;
    }

    private GestureDetectorCompat setupBackButtonDetector(Context context) {
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        return new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (postedLongClick) {
                    removeCallbacks(detectLongClick);
                }
                postedLongClick = true;
                postDelayed(detectLongClick, 200);
                onBackButton.run();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if ((postedLongClick || runningLongClick) && (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop)) {
                    postedLongClick = false;
                    runningLongClick = false;
                    removeCallbacks(detectLongClick);
                    removeCallbacks(onBackButton);
                }
                return false;
            }
        });
    }

    public void setViewToFindFocus(View viewToFindFocus) {
        this.viewToFindFocus = viewToFindFocus;
    }

    public void checkFindEditText() {
        if (editText == null && viewToFindFocus != null) {
            View focus = viewToFindFocus.findFocus();
            if (focus instanceof EditText) {
                editText = (EditText) focus;
            }
        }
    }

    public void setEditText(EditText editText) {
        this.editText = editText;
        dispatchBackWhenEmpty = false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int btnWidth = (getWidth() - AndroidUtilities.dp(SIDE_PADDING * 2 + BUTTON_PADDING * 2)) / 3;
        int btnHeight = (getHeight() - AndroidUtilities.dp(SIDE_PADDING * 3 + BUTTON_PADDING * 2)) / 4;

        for (int i = 0; i < views.length; i++) {
            int rowX = i % 3, rowY = i / 3;
            int left = rowX * (btnWidth + AndroidUtilities.dp(BUTTON_PADDING)) + AndroidUtilities.dp(SIDE_PADDING);
            int top = rowY * (btnHeight + AndroidUtilities.dp(BUTTON_PADDING)) + AndroidUtilities.dp(SIDE_PADDING);
            if (views[i] != null) {
                views[i].layout(left, top, left + btnWidth, top + btnHeight);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));

        int btnWidth = (getWidth() - AndroidUtilities.dp(SIDE_PADDING * 2 + BUTTON_PADDING * 2)) / 3;
        int btnHeight = (getHeight() - AndroidUtilities.dp(SIDE_PADDING * 3 + BUTTON_PADDING * 2)) / 4;

        for (View v : views) {
            if (v != null) {
                v.measure(MeasureSpec.makeMeasureSpec(btnWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(btnHeight, MeasureSpec.EXACTLY));
            }
        }
    }

    private static Drawable getButtonDrawable() {
        return Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_listSelector), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector), 60));
    }

    public void updateColors() {
        backButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        for (View v : views) {
            if (v != null) {
                v.setBackground(getButtonDrawable());

                if (v instanceof NumberButtonView) {
                    ((NumberButtonView) v).updateColors();
                }
            }
        }
    }

    private final static class NumberButtonView extends View {
        private TextPaint numberTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private TextPaint symbolsTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String mNumber, mSymbols;
        private Rect rect = new Rect();

        public NumberButtonView(Context context, String number, String symbols) {
            super(context);
            mNumber = number;
            mSymbols = symbols;

            numberTextPaint.setTextSize(AndroidUtilities.dp(24));
            symbolsTextPaint.setTextSize(AndroidUtilities.dp(14));

            setBackground(getButtonDrawable());
            updateColors();
        }

        private void updateColors() {
            numberTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            symbolsTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float symbolsWidth = symbolsTextPaint.measureText(mSymbols);
            float numberWidth = numberTextPaint.measureText(mNumber);

            numberTextPaint.getTextBounds(mNumber, 0, mNumber.length(), rect);
            float textOffsetNumber = rect.height() / 2f;
            symbolsTextPaint.getTextBounds(mSymbols, 0, mSymbols.length(), rect);
            float textOffsetSymbols = rect.height() / 2f;

            canvas.drawText(mNumber, getWidth() * 0.25f - numberWidth / 2f, getHeight() / 2f + textOffsetNumber, numberTextPaint);
            canvas.drawText(mSymbols, getWidth() * 0.7f - symbolsWidth / 2f, getHeight() / 2f + textOffsetSymbols, symbolsTextPaint);
        }
    }
}

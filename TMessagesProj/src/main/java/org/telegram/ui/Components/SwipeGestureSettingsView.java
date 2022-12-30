package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

public class SwipeGestureSettingsView extends FrameLayout {

    public static final int SWIPE_GESTURE_PIN = 0;
    public static final int SWIPE_GESTURE_READ = 1;
    public static final int SWIPE_GESTURE_ARCHIVE = 2;
    public static final int SWIPE_GESTURE_MUTE = 3;
    public static final int SWIPE_GESTURE_DELETE = 4;
    public static final int SWIPE_GESTURE_FOLDERS = 5;

    Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint filledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint pickerDividersPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RectF rect = new RectF();

    private NumberPicker picker;

    String[] strings = new String[6];
    String[] backgroundKeys = new String[6];
    RLottieDrawable[] icons = new RLottieDrawable[6];

    int currentIconIndex;
    RLottieImageView[] iconViews = new RLottieImageView[2];

    boolean hasTabs;
    float progressToSwipeFolders;
    float colorProgress = 1f;
    int fromColor;
    String currentColorKey;


    public SwipeGestureSettingsView(Context context, int currentAccount) {
        super(context);

        strings[SWIPE_GESTURE_PIN] = LocaleController.getString("SwipeSettingsPin", R.string.SwipeSettingsPin);
        strings[SWIPE_GESTURE_READ] = LocaleController.getString("SwipeSettingsRead", R.string.SwipeSettingsRead);
        strings[SWIPE_GESTURE_ARCHIVE] = LocaleController.getString("SwipeSettingsArchive", R.string.SwipeSettingsArchive);
        strings[SWIPE_GESTURE_MUTE] = LocaleController.getString("SwipeSettingsMute", R.string.SwipeSettingsMute);
        strings[SWIPE_GESTURE_DELETE] = LocaleController.getString("SwipeSettingsDelete", R.string.SwipeSettingsDelete);
        strings[SWIPE_GESTURE_FOLDERS] = LocaleController.getString("SwipeSettingsFolders", R.string.SwipeSettingsFolders);

        backgroundKeys[SWIPE_GESTURE_PIN] = Theme.key_chats_archiveBackground;
        backgroundKeys[SWIPE_GESTURE_READ] = Theme.key_chats_archiveBackground;
        backgroundKeys[SWIPE_GESTURE_ARCHIVE] = Theme.key_chats_archiveBackground;
        backgroundKeys[SWIPE_GESTURE_MUTE] = Theme.key_chats_archiveBackground;
        backgroundKeys[SWIPE_GESTURE_DELETE] = Theme.key_dialogSwipeRemove;
        backgroundKeys[SWIPE_GESTURE_FOLDERS] = Theme.key_chats_archivePinBackground;

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(1));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(AndroidUtilities.dp(5));

        pickerDividersPaint.setStyle(Paint.Style.STROKE);
        pickerDividersPaint.setStrokeCap(Paint.Cap.ROUND);
        pickerDividersPaint.setStrokeWidth(AndroidUtilities.dp(2));

        picker = new NumberPicker(context, 13) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float y = AndroidUtilities.dp(31);
                pickerDividersPaint.setColor(Theme.getColor(Theme.key_radioBackgroundChecked));
                canvas.drawLine(AndroidUtilities.dp(2), y, getMeasuredWidth() - AndroidUtilities.dp(2), y, pickerDividersPaint);

                y = getMeasuredHeight() - AndroidUtilities.dp(31);
                canvas.drawLine(AndroidUtilities.dp(2), y, getMeasuredWidth() - AndroidUtilities.dp(2), y, pickerDividersPaint);
            }
        };
        picker.setMinValue(0);
        picker.setDrawDividers(false);
        hasTabs = !MessagesController.getInstance(currentAccount).dialogFilters.isEmpty();
        picker.setMaxValue(hasTabs ? strings.length - 1 : strings.length - 2);
        picker.setAllItemsCount(hasTabs ? strings.length : strings.length - 1);
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(value -> strings[value]);
        picker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            swapIcons();

            SharedConfig.updateChatListSwipeSetting(newVal);
            invalidate();
            picker.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        });
        picker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        picker.setValue(SharedConfig.getChatSwipeAction(currentAccount));

        addView(picker, LayoutHelper.createFrame(132, LayoutHelper.MATCH_PARENT, Gravity.RIGHT, 21, 0, 21, 0));

        setWillNotDraw(false);

        currentIconIndex = 0;
        for (int i = 0; i < 2; i++) {
            iconViews[i] = new RLottieImageView(context);
            addView(iconViews[i], LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0,  132 + 21 + 21 + 10, 0));
        }

        RLottieDrawable currentIcon = getIcon(picker.getValue());
        if (currentIcon != null) {
            iconViews[0].setImageDrawable(currentIcon);
            currentIcon.setCurrentFrame(currentIcon.getFramesCount() - 1);
        }
        AndroidUtilities.updateViewVisibilityAnimated(iconViews[0], true, 0.5f, false);
        AndroidUtilities.updateViewVisibilityAnimated(iconViews[1], false, 0.5f, false);

        progressToSwipeFolders = picker.getValue() == SWIPE_GESTURE_FOLDERS ? 1f : 0;
        currentIconValue = picker.getValue();

    }

    int currentIconValue;
    Runnable swapIconRunnable;
    private void swapIcons() {
        if (swapIconRunnable != null) {
            return;
        }
        int newValue = picker.getValue();
        if (currentIconValue != newValue) {
            currentIconValue = newValue;
            int nextIconIndex = (currentIconIndex + 1) % 2;
            RLottieDrawable drawable = getIcon(newValue);
            if (drawable != null) {
                if (iconViews[nextIconIndex].getVisibility() != View.VISIBLE) {
                    drawable.setCurrentFrame(0, false);
                }
                iconViews[nextIconIndex].setAnimation(drawable);
                iconViews[nextIconIndex].playAnimation();
            } else {
                iconViews[nextIconIndex].clearAnimationDrawable();
            }
            AndroidUtilities.updateViewVisibilityAnimated(iconViews[currentIconIndex], false, 0.5f, true);
            AndroidUtilities.updateViewVisibilityAnimated(iconViews[nextIconIndex], true, 0.5f, true);
            currentIconIndex = nextIconIndex;

            AndroidUtilities.runOnUIThread(swapIconRunnable = () -> {
                swapIconRunnable = null;
                swapIcons();
            }, 150);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(102), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean changeFolder = picker.getValue() == SWIPE_GESTURE_FOLDERS;

        if (changeFolder && progressToSwipeFolders != 1f) {
            progressToSwipeFolders += 16 / 300f;
            if (progressToSwipeFolders > 1f) {
                progressToSwipeFolders = 1f;
            } else {
                iconViews[0].invalidate();
                iconViews[1].invalidate();
                invalidate();
            }
        } else if (!changeFolder && progressToSwipeFolders != 0) {
            progressToSwipeFolders -= 16 / 300f;
            if (progressToSwipeFolders < 0f) {
                progressToSwipeFolders = 0f;
            } else {
                iconViews[0].invalidate();
                iconViews[1].invalidate();
                invalidate();
            }
        }
        outlinePaint.setColor(Theme.getColor(Theme.key_switchTrack));

        linePaint.setColor(Theme.getColor(Theme.key_switchTrack));

        int right = getMeasuredWidth() - (AndroidUtilities.dp(132) + AndroidUtilities.dp(21) + AndroidUtilities.dp(16));
        int left = AndroidUtilities.dp(21);

        int verticalPadding = (getMeasuredHeight() - AndroidUtilities.dp(48)) / 2;

        rect.set(left, verticalPadding, right, getMeasuredHeight() - verticalPadding);


        int color;
        if (currentColorKey == null) {
            currentColorKey = backgroundKeys[picker.getValue()];
            colorProgress = 1f;
            color = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(currentColorKey), 0.9f);
            fromColor = color;
        } else if (!backgroundKeys[picker.getValue()].equals(currentColorKey)) {
            fromColor = ColorUtils.blendARGB(fromColor, ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(currentColorKey), 0.9f), colorProgress);
            colorProgress = 0;
            currentColorKey = backgroundKeys[picker.getValue()];
        }

        if (colorProgress != 1f) {
            colorProgress += 16 / 100f;
            if (colorProgress > 1f) {
                colorProgress = 1f;
            } else {
                invalidate();
            }
        }

        color = ColorUtils.blendARGB(fromColor, ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(currentColorKey), 0.9f), colorProgress);

        filledPaint.setColor(color);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), filledPaint);

        filledPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        filledPaint.setAlpha(255);


        //
//        float y = rect.centerY() - AndroidUtilities.dp(6);
//        linePaint.setAlpha((int) (57 * progressToSwipeFolders));
//        canvas.drawLine(rect.left + AndroidUtilities.dp(23) + AndroidUtilities.dp(15 + 8), y, rect.right - AndroidUtilities.dp(50), y, linePaint);
//
//        y = rect.centerY() + AndroidUtilities.dp(6);
//        canvas.drawLine(rect.left + AndroidUtilities.dp(23) + AndroidUtilities.dp(15 + 8), y, rect.right - AndroidUtilities.dp(16), y, linePaint);
//        //

       // rect.set(left, verticalPadding, right - AndroidUtilities.dp(58) * (1f - progressToSwipeFolders), getMeasuredHeight() - verticalPadding);
        rect.set(left, verticalPadding, right - AndroidUtilities.dp(58), getMeasuredHeight() - verticalPadding);

        rect.inset(-AndroidUtilities.dp(1), -AndroidUtilities.dp(1));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), filledPaint);
        outlinePaint.setAlpha(31);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), outlinePaint);
//
        canvas.save();
        canvas.clipRect(rect);

       // float leftOffset = AndroidUtilities.dp(15 + 8) * progressToSwipeFolders;
        float leftOffset = 0;

        filledPaint.setColor(Theme.getColor(Theme.key_switchTrack));
        filledPaint.setAlpha(60);
        canvas.drawCircle(rect.left + leftOffset, rect.centerY(), AndroidUtilities.dp(15), filledPaint);

        float y = rect.centerY() - AndroidUtilities.dp(6);
        linePaint.setAlpha(57);
        canvas.drawLine(rect.left + AndroidUtilities.dp(23) + leftOffset, y, rect.right - AndroidUtilities.dp(68), y, linePaint);

        y = rect.centerY() + AndroidUtilities.dp(6);
        canvas.drawLine(rect.left + AndroidUtilities.dp(23) + leftOffset, y, rect.right - AndroidUtilities.dp(23), y, linePaint);
        canvas.restore();
    }

    public RLottieDrawable getIcon(int i) {
        if (icons[i] == null) {
            int rawId;
            switch (i) {
                default:
                case SWIPE_GESTURE_PIN:
                    rawId = R.raw.swipe_pin;
                    break;
                case SWIPE_GESTURE_ARCHIVE:
                    rawId = R.raw.chats_archive;
                    break;
                case SWIPE_GESTURE_DELETE:
                    rawId = R.raw.swipe_delete;
                    break;
                case SWIPE_GESTURE_MUTE:
                    rawId = R.raw.swipe_mute;
                    break;
                case SWIPE_GESTURE_READ:
                    rawId = R.raw.swipe_read;
                    break;
                case SWIPE_GESTURE_FOLDERS:
                    rawId = R.raw.swipe_disabled;
                    break;
            }
            icons[i] = new RLottieDrawable(rawId, "" + rawId, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
            updateIconColor(i);
        }

        return icons[i];
    }

    public void updateIconColor(int i) {
        if (icons[i] != null) {
            int backgroundColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_chats_archiveBackground), 0.9f);//Theme.getColor(Theme.key_chats_archiveBackground);
            int iconColor = Theme.getColor(Theme.key_chats_archiveIcon);
            if (i == SWIPE_GESTURE_ARCHIVE) {
                icons[i].setLayerColor("Arrow.**", backgroundColor);
                icons[i].setLayerColor("Box2.**",iconColor);
                icons[i].setLayerColor("Box1.**",iconColor);
            } else {
                icons[i].setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            }
        }
    }

    public void updateColors() {
        for(int i = 0; i < icons.length; i++) {
            updateIconColor(i);
        }
    }

    @Override
    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
        updateColors();
        picker.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        picker.invalidate();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(true);
        info.setContentDescription(strings[picker.getValue()]);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null));
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            int newValue = picker.getValue() + 1;
            if (newValue > picker.getMaxValue() || newValue < 0) {
                newValue = 0;
            }
            setContentDescription(strings[newValue]);
            picker.changeValueByOne(true);
        }
    }
}

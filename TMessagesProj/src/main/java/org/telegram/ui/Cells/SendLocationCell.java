/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.ShareLocationDrawable;

public class SendLocationCell extends FrameLayout {

    private int currentAccount = UserConfig.selectedAccount;
    private SimpleTextView accurateTextView;
    private SimpleTextView titleTextView;
    private ImageView imageView;
    private long dialogId;
    private RectF rect;
    private boolean live;
    private boolean liveDisable;
    private final Theme.ResourcesProvider resourcesProvider;
    public boolean useDivider;

    private Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            checkText();
            invalidate((int) rect.left - 5, (int) rect.top - 5, (int) rect.right + 5, (int) rect.bottom + 5);
            AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
        }
    };

    public SendLocationCell(Context context, boolean live, boolean liveDisable, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.live = live;
        this.liveDisable = liveDisable;

        imageView = new ImageView(context);
        addView(imageView, LayoutHelper.createFrame(46, 46, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 13, 0, LocaleController.isRTL ? 13 : 0, 0));

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextSize(16);
        titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.bold());
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 16 : 73, 9.33f, LocaleController.isRTL ? 73 : 16, 0));

        accurateTextView = new SimpleTextView(context);
        accurateTextView.setTextSize(14);
        accurateTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
        accurateTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(accurateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 16 : 73, 33, LocaleController.isRTL ? 73 : 16, 0));

        updateImage();

        setWillNotDraw(false);
    }
    private void updateImage() {
        titleTextView.setTag(live ? liveDisable ? Theme.key_text_RedBold : Theme.key_location_sendLiveLocationText : Theme.key_location_sendLocationText);
        titleTextView.setTextColor(getThemedColor(live ? liveDisable ? Theme.key_text_RedBold : Theme.key_location_sendLiveLocationText : Theme.key_location_sendLocationText));

        imageView.setTag(live ? liveDisable ? Theme.key_color_red : Theme.key_location_sendLiveLocationBackground + Theme.key_location_sendLiveLocationIcon : Theme.key_location_sendLocationBackground + Theme.key_location_sendLocationIcon);
        Drawable circle = Theme.createSimpleSelectorCircleDrawable(dp(46), getThemedColor(live ? liveDisable ? Theme.key_color_red : Theme.key_location_sendLiveLocationBackground : Theme.key_location_sendLocationBackground), getThemedColor(live ? liveDisable ? Theme.key_color_red : Theme.key_location_sendLiveLocationBackground : Theme.key_location_sendLocationBackground));
        if (live) {
            rect = new RectF();
            Drawable drawable = new ShareLocationDrawable(getContext(), liveDisable ? ShareLocationDrawable.TYPE_DISABLE : ShareLocationDrawable.TYPE_ADD);
            drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_sendLiveLocationIcon), PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(circle, drawable);
            combinedDrawable.setCustomSize(dp(46), dp(46));
            imageView.setBackgroundDrawable(combinedDrawable);
            if (!liveDisable) {
                AndroidUtilities.cancelRunOnUIThread(invalidateRunnable);
                AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
            }
        } else {
            Drawable drawable = getResources().getDrawable(R.drawable.pin).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_sendLocationIcon), PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(circle, drawable);
            combinedDrawable.setCustomSize(dp(46), dp(46));
            combinedDrawable.setIconSize(dp(24), dp(24));
            imageView.setBackgroundDrawable(combinedDrawable);
        }
    }

    private ImageView getImageView() {
        return imageView;
    }

    public void setHasLocation(boolean value) {
        LocationController.SharingLocationInfo info = LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId);
        if (info == null) {
            titleTextView.setAlpha(value ? 1.0f : 0.5f);
            accurateTextView.setAlpha(value ? 1.0f : 0.5f);
            imageView.setAlpha(value ? 1.0f : 0.5f);
        }
        if (live) {
            checkText();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(invalidateRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rect != null) {
            AndroidUtilities.cancelRunOnUIThread(invalidateRunnable);
            AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
        }
    }

    public void setText(String title, String text) {
        titleTextView.setText(title);
        accurateTextView.setText(text);
    }

    public void setDialogId(long did) {
        dialogId = did;
        if (live) {
            checkText();
        }
    }

    private void checkText() {
        LocationController.SharingLocationInfo info = LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId);
        if (info != null) {
            if (liveDisable) {
                setText(LocaleController.getString(R.string.StopLiveLocation), LocaleController.formatLocationUpdateDate(info.messageObject.messageOwner.edit_date != 0 ? info.messageObject.messageOwner.edit_date : info.messageObject.messageOwner.date));
            } else {
                setText(LocaleController.getString(R.string.SharingLiveLocation), LocaleController.getString(R.string.SharingLiveLocationAdd));
            }
        } else {
            setText(LocaleController.getString(R.string.SendLiveLocation), LocaleController.getString(R.string.SendLiveLocationInfo));
        }
    }

    private final AnimatedFloat progress = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat progressAlpha = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat progressScale = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedTextView.AnimatedTextDrawable textDrawable = new AnimatedTextView.AnimatedTextDrawable(false, true, false);
    {
        textDrawable.setAnimationProperties(.3f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setTextSize(dp(12));
        textDrawable.setTypeface(Typeface.DEFAULT_BOLD);
        textDrawable.setGravity(Gravity.CENTER);
        textDrawable.setCallback(this);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == textDrawable || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (useDivider) {
            Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
            if (dividerPaint != null) {
                canvas.drawRect(LocaleController.isRTL ? 0 : dp(73), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(73) : 0), getMeasuredHeight(), dividerPaint);
            }
        }

        if (liveDisable) {
            return;
        }
        LocationController.SharingLocationInfo currentInfo = LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId);
        float progress = this.progress.get();

        float alpha;
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (currentInfo != null && currentInfo.stopTime >= currentTime && currentInfo.period != 0x7FFFFFFF) {
            progress = Math.abs(currentInfo.stopTime - currentTime) / (float) currentInfo.period;
            alpha = this.progressAlpha.set(true);
        } else {
            alpha = this.progressAlpha.set(false);
        }

        if (alpha <= 0) {
            return;
        }

        if (LocaleController.isRTL) {
            rect.set(dp(13), getMeasuredHeight() / 2f - dp(15), dp(43), getMeasuredHeight() / 2f + dp(15));
        } else {
            rect.set(getMeasuredWidth() - dp(43), getMeasuredHeight() / 2f - dp(15), getMeasuredWidth() - dp(13), getMeasuredHeight() / 2f + dp(15));
        }

        canvas.save();
        final float s = AndroidUtilities.lerp(.6f, 1f, alpha);
        canvas.scale(s, s, rect.centerX(), rect.centerY());

        int color = getThemedColor(Theme.key_location_liveLocationProgress);
        Theme.chat_radialProgress2Paint.setColor(color);

        int a = Theme.chat_radialProgress2Paint.getAlpha();
        Theme.chat_radialProgress2Paint.setAlpha((int) (.20f * a * alpha));
        canvas.drawArc(rect, -90, 360, false, Theme.chat_radialProgress2Paint);
        Theme.chat_radialProgress2Paint.setAlpha((int) (a * alpha));
        canvas.drawArc(rect, -90, -360 * this.progress.set(progress), false, Theme.chat_radialProgress2Paint);
        Theme.chat_radialProgress2Paint.setAlpha(a);

        if (currentInfo != null) {
            textDrawable.setText(LocaleController.formatLocationLeftTime(Math.abs(currentInfo.stopTime - currentTime)));
        }
        int len = textDrawable.getText().length();
        final float s2 = progressScale.set(len > 4 ? .75f : (len > 3 ? .85f : 1f));
        canvas.scale(s2, s2, rect.centerX(), rect.centerY());
        textDrawable.setTextColor(color);
        textDrawable.setAlpha((int) (0xFF * alpha));
        textDrawable.setBounds((int) rect.left, (int) (rect.centerY() - dp(12 + 1)), (int) rect.right, (int) (rect.centerY() + dp(12)));
        textDrawable.draw(canvas);

        canvas.restore();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.Storage.CacheModel;

public class StorageDiagramView extends View implements NotificationCenter.NotificationCenterDelegate {

    private RectF rectF = new RectF();
    private ClearViewData[] data;
    private float[] drawingPercentage;
    private float[] animateToPercentage;
    private float[] startFromPercentage;

    private float singleProgress = 0;

    private AvatarDrawable avatarDrawable;
    private ImageReceiver avatarImageReceiver;
    private Long dialogId;

    AnimatedTextView.AnimatedTextDrawable text1 = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
    AnimatedTextView.AnimatedTextDrawable text2 = new AnimatedTextView.AnimatedTextDrawable(false, true, false);

    CharSequence dialogText;
    TextPaint dialogTextPaint;
    StaticLayout dialogTextLayout;

    int enabledCount;

    ValueAnimator valueAnimator;
    CacheModel cacheModel;

    public StorageDiagramView(Context context) {
        super(context);
        text1.setCallback(this);
        text2.setCallback(this);
    }

    public void setCacheModel(CacheModel cacheModel) {
        this.cacheModel = cacheModel;
    }

    public StorageDiagramView(Context context, long dialogId) {
        this(context);
        this.dialogId = dialogId;

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setScaleSize(1.5f);
        avatarImageReceiver = new ImageReceiver();
        avatarImageReceiver.setParentView(this);

        if (dialogId == CacheControlActivity.UNKNOWN_CHATS_DIALOG_ID) {
            dialogText = LocaleController.getString(R.string.CacheOtherChats);
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_OTHER_CHATS);
            avatarImageReceiver.setForUserOrChat(null, avatarDrawable);
        } else {
            TLObject dialog = MessagesController.getInstance(UserConfig.selectedAccount).getUserOrChat(dialogId);
            dialogText = DialogObject.setDialogPhotoTitle(avatarImageReceiver, avatarDrawable, dialog);
            dialogText = Emoji.replaceEmoji(dialogText, null, AndroidUtilities.dp(6), false);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int left;
        if (dialogId != null) {
            super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(166), MeasureSpec.EXACTLY)
            );
            int w = MeasureSpec.getSize(widthMeasureSpec);
            left = (w - AndroidUtilities.dp(110)) / 2;
            rectF.set(left + AndroidUtilities.dp(3), AndroidUtilities.dp(3), left + AndroidUtilities.dp(110 - 3), AndroidUtilities.dp(110 - 3));
        } else {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY)
            );
            left = 0;
            rectF.set(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(110 - 3), AndroidUtilities.dp(110 - 3));
        }

        text1.setAnimationProperties(.18f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        text1.setTextSize(AndroidUtilities.dp(24));
        text1.setTypeface(AndroidUtilities.bold());

        text2.setAnimationProperties(.18f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);

        if (dialogId != null) {
            text2.setTextSize(AndroidUtilities.dp(16));
            text1.setGravity(Gravity.RIGHT);
            text2.setGravity(Gravity.LEFT);
        } else {
            text2.setTextSize(AndroidUtilities.dp(13));
            int t1h = (int) text1.getTextSize(), t2h = (int) text2.getTextSize();
            int top = (int) (AndroidUtilities.dp(110) - t1h - t2h) / 2;
            text1.setBounds(0, top, getMeasuredWidth(), top + t1h);
            text2.setBounds(0, top + t1h + AndroidUtilities.dp(2), getMeasuredWidth(), top + t1h + t2h + AndroidUtilities.dp(2));
            text1.setGravity(Gravity.CENTER);
            text2.setGravity(Gravity.CENTER);
        }

        if (dialogText != null) {
            if (dialogTextPaint == null) {
                dialogTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            }
            dialogTextPaint.setTextSize(AndroidUtilities.dp(13));
            int width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(60);
            dialogTextLayout = StaticLayoutEx.createStaticLayout2(dialogText, dialogTextPaint, width, Layout.Alignment.ALIGN_CENTER, 1, 0, false, TextUtils.TruncateAt.END, width, 1);
        }
        if (avatarImageReceiver != null) {
            avatarImageReceiver.setImageCoords(left + AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(90), AndroidUtilities.dp(90));
            avatarImageReceiver.setRoundRadius(AndroidUtilities.dp(45));
        }

        updateDescription();
    }

    public void setData(CacheModel cacheModel, ClearViewData[] data) {
        this.data = data;
        this.cacheModel = cacheModel;
        invalidate();
        drawingPercentage = new float[data.length];
        animateToPercentage = new float[data.length];
        startFromPercentage = new float[data.length];

        update(false);

        if (enabledCount > 1) {
            singleProgress = 0;
        } else {
            singleProgress = 1f;
        }
    }

    private long lastDrawTime;

    @Override
    protected void onDraw(Canvas canvas) {
        if (data == null) {
            return;
        }

        if (avatarImageReceiver != null) {
            canvas.save();
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / 100f;
                pressedProgress = Utilities.clamp(pressedProgress, 1f, 0);
                invalidate();
            }
            float s = 0.85f + 0.15f * (1f - pressedProgress);
            canvas.scale(s, s, avatarImageReceiver.getCenterX(), avatarImageReceiver.getCenterY());
        }

        if (enabledCount > 1) {
            if (singleProgress > 0) {
                singleProgress -= 0.04;
                if (singleProgress < 0) {
                    singleProgress = 0;
                }
            }
        } else {
            if (singleProgress < 1f) {
                singleProgress += 0.04;
                if (singleProgress > 1f) {
                    singleProgress = 1f;
                }
            }
        }

        float startFrom = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null || drawingPercentage[i] == 0) {
                continue;
            }
            float percent = drawingPercentage[i];
            if (data[i].firstDraw) {
                float a = -360 * percent + (1f - singleProgress) * 10;
                if (a > 0) {
                    a = 0;
                }
                data[i].paint.setColor(Theme.getColor(data[i].colorKey));
                data[i].paint.setAlpha(255);
                float r = (rectF.width() / 2);
                float len = (float) ((Math.PI * r / 180) * a);
                if (Math.abs(len) <= 1f) {
                    float x = rectF.centerX() + (float) (r * Math.cos(Math.toRadians(-90 - 360 * startFrom)));
                    float y = rectF.centerY() + (float) (r * Math.sin(Math.toRadians(-90 - 360 * startFrom)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        canvas.drawPoint(x,y,data[i].paint);
                    } else {
                        data[i].paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(x, y, data[i].paint.getStrokeWidth() / 2, data[i].paint);
                    }
                } else {
                    data[i].paint.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(rectF, -90 - 360 * startFrom, a, false, data[i].paint);
                }
            }
            startFrom += percent;
        }

        startFrom = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null || drawingPercentage[i] == 0) {
                continue;
            }
            float percent = drawingPercentage[i];
            if (!data[i].firstDraw) {
                float a = -360 * percent + (1f - singleProgress) * 10;
                if (a > 0) {
                    a = 0;
                }
                data[i].paint.setColor(Theme.getColor(data[i].colorKey));
                data[i].paint.setAlpha(255);
                float r = (rectF.width() / 2);
                float len = (float) ((Math.PI * r / 180) * a);
                if (Math.abs(len) <= 1f) {
                    float x = rectF.centerX() + (float) (r * Math.cos(Math.toRadians(-90 - 360 * startFrom)));
                    float y = rectF.centerY() + (float) (r * Math.sin(Math.toRadians(-90 - 360 * startFrom)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        canvas.drawPoint(x,y,data[i].paint);
                    } else {
                        data[i].paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(x, y, data[i].paint.getStrokeWidth() / 2, data[i].paint);
                    }
                } else {
                    data[i].paint.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(rectF, -90 - 360 * startFrom, a, false, data[i].paint);
                }
            }
            startFrom += percent;
        }

        if (avatarImageReceiver != null) {
            avatarImageReceiver.draw(canvas);
            canvas.restore();
        }

        if (text1 != null) {
            text1.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            text2.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            if (dialogId != null) {
                float textWidth = text1.getCurrentWidth() + AndroidUtilities.dp(4) + text2.getCurrentWidth();
                float leftpad = (getWidth() - textWidth) / 2;
                text1.setBounds(0, AndroidUtilities.dp(115), (int) (leftpad + text1.getCurrentWidth()), AndroidUtilities.dp(115 + 30));
                text2.setBounds((int) (leftpad + textWidth - text2.getCurrentWidth()), AndroidUtilities.dp(115 + 3), getWidth(), AndroidUtilities.dp(115 + 3 + 30));
            }
            text1.draw(canvas);
            text2.draw(canvas);
        }

        if (dialogTextLayout != null) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(30), AndroidUtilities.dp(148) - (dialogTextLayout.getHeight() - AndroidUtilities.dp(13)) / 2f);
            dialogTextPaint.setColor(Theme.getColor(Theme.key_dialogTextBlack));
            dialogTextLayout.draw(canvas);
            canvas.restore();
        }
    }

    public static class ClearViewData {

        public int colorKey;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public boolean clear = true;
        boolean firstDraw = false;
        public long size;

        private final StorageDiagramView parentView;

        public ClearViewData(StorageDiagramView parentView) {
            this.parentView = parentView;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setClear(boolean clear) {
            if (this.clear != clear) {
                this.clear = clear;
                firstDraw = true;
            }
        }
    }

    public void update(boolean animate) {
        long total = 0;
        ClearViewData[] data = this.data;
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.length; i++) {
            long cacheModelSize = cacheModel.getSelectedFilesSize(i);
            if (data[i] == null || (!data[i].clear && cacheModelSize <= 0)) {
                continue;
            }
            total += cacheModelSize > 0 ? cacheModelSize : data[i].size;
        }

        float k = 0;
        float max= 0;
        enabledCount = 0;

        for (int i = 0; i < data.length; i++) {
            long cacheModelSize = cacheModel.getSelectedFilesSize(i);
            if (data[i] != null) {
                if (data[i].clear || cacheModelSize > 0) {
                    enabledCount++;
                }
            }

            if (data[i] == null || (!data[i].clear && cacheModelSize <= 0)) {
                animateToPercentage[i] = 0;
                continue;
            }
            long size = cacheModelSize > 0 ? cacheModelSize : data[i].size;;
            float percent = size / (float) total;
            if (percent < 0.02777f) {
                percent = 0.02777f;
            }
            k += percent;
            if (percent > max && (data[i].clear || cacheModelSize > 0)) {
                max = percent;
            }
            animateToPercentage[i] = percent;
        }
        if (k > 1) {
            float l = 1f / k;
            for (int i = 0; i < data.length; i++) {
                if (data[i] == null) {
                    continue;
                }
                animateToPercentage[i] *= l;
            }
        }

        if (!animate) {
            System.arraycopy(animateToPercentage, 0, drawingPercentage, 0, data.length);
        } else {
            System.arraycopy(drawingPercentage, 0, startFromPercentage, 0, data.length);

            if (valueAnimator != null) {
                valueAnimator.removeAllListeners();
                valueAnimator.cancel();
            }
            valueAnimator = ValueAnimator.ofFloat(0, 1f);
            valueAnimator.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();
                for (int i = 0; i < data.length; i++) {
                    drawingPercentage[i] = startFromPercentage[i] * (1f - v) + animateToPercentage[i] * v;
                }
                invalidate();
            });
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    for (int i = 0; i < data.length; i++) {
                        if (data[i] != null) {
                            data[i].firstDraw = false;
                        }
                    }
                }
            });
            valueAnimator.setDuration(450);
            valueAnimator.setInterpolator(new FastOutSlowInInterpolator());
            valueAnimator.start();
        }
    }

    protected void onAvatarClick() {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean hitAvatar = (
            avatarImageReceiver != null && dialogId != null && dialogId != CacheControlActivity.UNKNOWN_CHATS_DIALOG_ID &&
            event.getX() > avatarImageReceiver.getImageX() && event.getX() <= avatarImageReceiver.getImageX2() &&
            event.getY() > avatarImageReceiver.getImageY() && event.getY() <= avatarImageReceiver.getImageY2()
        );
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (hitAvatar) {
                setPressed(true);
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (hitAvatar && event.getAction() != MotionEvent.ACTION_CANCEL) {
                AndroidUtilities.runOnUIThread(this::onAvatarClick, 80);
            }
            setPressed(false);
            return true;
        }
        return super.onTouchEvent(event);
    }

    float pressedProgress;
    ValueAnimator backAnimator;

    @Override
    public void setPressed(boolean pressed) {
        if (isPressed() != pressed) {
            super.setPressed(pressed);
            invalidate();
            if (pressed) {
                if (backAnimator != null) {
                    backAnimator.removeAllListeners();
                    backAnimator.cancel();
                }
            }
            if (!pressed && pressedProgress != 0) {
                backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                backAnimator.addUpdateListener(animation -> {
                    pressedProgress = (float) animation.getAnimatedValue();
                    invalidate();
                });
                backAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        backAnimator = null;
                    }
                });
                backAnimator.setInterpolator(new OvershootInterpolator(2f));
                backAnimator.setDuration(350);
                backAnimator.start();
            }
        }
    }

    public long updateDescription() {
        long total = calculateSize();
        String[] str = AndroidUtilities.formatFileSize(total).split(" ");
        if (str.length > 1) {
            text1.setText(total == 0 ? " " : str[0], true, false);
            text2.setText(total == 0 ? " " : str[1], true, false);
        }
        return total;
    }

    public long calculateSize() {
        if (data == null) {
            return 0;
        }
        long total = 0;
        for (int i = 0; i < data.length; i++) {
            long cacheModelSize = cacheModel.getSelectedFilesSize(i);
            if (data[i] == null || (!data[i].clear && cacheModelSize <= 0)) {
                continue;
            }
            total += cacheModelSize > 0 ? cacheModelSize : data[i].size;
        }
        return total;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (avatarImageReceiver != null) {
            avatarImageReceiver.onAttachedToWindow();
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (avatarImageReceiver != null) {
            avatarImageReceiver.onDetachedFromWindow();
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            invalidate();
        }
    }
}

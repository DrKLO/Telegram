package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotInlineKeyboard;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.Text;
import org.telegram.ui.LinkManager;

import java.util.Arrays;

class BotButton {
    public final Runnable invalidateRunnable;

    public boolean isSeparator;
    public float x;
    public int y;
    public float width;
    public int height;
    public int positionFlags;
    public Text title;
    @Nullable
    public TLRPC.KeyboardButton button;
    @Nullable
    public BotInlineKeyboard.ButtonCustom buttonCustom;
    public BotInlineKeyboard.Button buttonImpl;
    public TLRPC.TL_reactionCount reaction;
    public int angle;
    public float progressAlpha;
    public long lastUpdateTime;
    public boolean isInviteButton;
    public boolean isLocked;

    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF loadingRect = new RectF();
    private final float[] radii = new float[8];

    public LoadingDrawable loadingDrawable;
    public Drawable selectorDrawable;
    public Drawable iconDrawable;
    public AnimatedEmojiDrawable animatedEmojiDrawable;

    public boolean pressed;
    public float pressT;
    public ValueAnimator pressAnimator;

    public BotButton(Runnable invalidateRunnable) {
        this.invalidateRunnable = invalidateRunnable;
    }

    public boolean draw(
        Canvas canvas,
        RectF rect,
        boolean drawProgress,
        boolean drawBuyCard,
        Theme.ResourcesProvider resourcesProvider
    ) {
        boolean invalidate = false;
        final float s = getPressScale();

        canvas.save();
        if (s != 1) {
            canvas.scale(s, s, rect.centerX(), rect.centerY());
        }
        Arrays.fill(radii, dp(Math.min(6.75f, SharedConfig.bubbleRadius)));
        if (hasPositionFlag(MessageObject.POSITION_FLAG_LEFT | MessageObject.POSITION_FLAG_BOTTOM)) {
            radii[6] = radii[7] = dp(SharedConfig.bubbleRadius);
        }
        if (hasPositionFlag(MessageObject.POSITION_FLAG_RIGHT | MessageObject.POSITION_FLAG_BOTTOM)) {
            radii[4] = radii[5] = dp(SharedConfig.bubbleRadius);
        }

        path.rewind();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        canvas.drawPath(path, Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider));

        final BotInlineKeyboard.BackgroundColor bgColor =
            buttonImpl != null ? buttonImpl.getColor() :
            BotInlineKeyboard.BackgroundColor.NONE;

        if (bgColor != BotInlineKeyboard.BackgroundColor.NONE) {
            switch (bgColor) {
                case DANGER:
                    paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_botKeyboard_button_danger, resourcesProvider), 0.7f));
                    break;
                case SUCCESS:
                    paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_botKeyboard_button_success, resourcesProvider), 0.7f));
                    break;
                case PRIMARY:
                    paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_botKeyboard_button_primary, resourcesProvider), 0.7f));
                    break;
            }
            canvas.drawPath(path, paint);
        }
        final boolean hasGradientService = resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
        if (hasGradientService && (bgColor == BotInlineKeyboard.BackgroundColor.NONE || resourcesProvider != null && resourcesProvider.isDark())) {
            canvas.drawPath(path, Theme.chat_actionBackgroundGradientDarkenPaint);
        }

//        boolean drawProgress = (button instanceof TLRPC.TL_keyboardButtonCallback || button instanceof TLRPC.TL_keyboardButtonGame || button instanceof TLRPC.TL_keyboardButtonBuy || button instanceof TLRPC.TL_keyboardButtonUrlAuth) && SendMessagesHelper.getInstance(currentAccount).isSendingCallback(currentMessageObject, button)
//                || button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation && SendMessagesHelper.getInstance(currentAccount).isSendingCurrentLocation(currentMessageObject, button)
//                || button instanceof TLRPC.TL_keyboardButtonUrl && delegate != null && delegate.isProgressLoading(this, ChatActivity.PROGRESS_BOT_BUTTON) && delegate.getProgressLoadingBotButtonUrl(this) == button.url;
//
//        if (button.buttonCustom != null && currentMessageObject != null) {
//            if (button.buttonCustom.id == BotInlineKeyboard.ButtonCustom.SUGGESTION_ACCEPT) {
//                drawProgress |= MessagesController.getInstance(currentAccount).isSendingSuggestedMessageApproval(currentMessageObject.getDialogId(), currentMessageObject.getId(), true);
//            } else if (button.buttonCustom.id == BotInlineKeyboard.ButtonCustom.SUGGESTION_DECLINE) {
//                drawProgress |= MessagesController.getInstance(currentAccount).isSendingSuggestedMessageApproval(currentMessageObject.getDialogId(), currentMessageObject.getId(), false);
//            }
//        }

        canvas.save();
        canvas.clipPath(path);
        if (drawProgress) {
            if (loadingDrawable == null) {
                loadingDrawable = new LoadingDrawable();
                loadingDrawable.setRadiiDp(5.5f);
                loadingDrawable.setAppearByGradient(true);
                loadingDrawable.strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.25f));
            } else if (loadingDrawable.isDisappeared() || loadingDrawable.isDisappearing()) {
                loadingDrawable.reset();
                loadingDrawable.resetDisappear();
            }
        } else if (loadingDrawable != null && !loadingDrawable.isDisappearing() && !loadingDrawable.isDisappeared()) {
            loadingDrawable.disappear();
        }

        if (loadingDrawable != null && (drawProgress || loadingDrawable.isDisappearing())) {
            loadingRect.set(rect);
            loadingRect.inset(AndroidUtilities.dpf2(.625f), AndroidUtilities.dpf2(.625f));
            loadingDrawable.setRadii(radii);
            loadingDrawable.setBounds(loadingRect);
            loadingDrawable.setColors(
                Theme.multAlpha(Theme.getColor(Theme.key_chat_serviceBackgroundSelector, resourcesProvider), 1f),
                Theme.multAlpha(Theme.getColor(Theme.key_chat_serviceBackgroundSelector, resourcesProvider), 2.5f),
                Theme.multAlpha(Theme.getColor(Theme.key_chat_serviceBackgroundSelector, resourcesProvider), 3f),
                Theme.multAlpha(Theme.getColor(Theme.key_chat_serviceBackgroundSelector, resourcesProvider), 10f)
            );
            loadingDrawable.setAlpha(0xFF);
            loadingDrawable.draw(canvas);
            invalidate = true;
        }

        if (selectorDrawable != null) {
            selectorDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
            selectorDrawable.setAlpha(0xFF);
            selectorDrawable.draw(canvas);
        }
        canvas.restore();

        canvas.save();
        int iconWithMarginPx = iconDrawable != null || animatedEmojiDrawable != null ? dp(24 + 2) : 0;
        float titleX = rect.left + (rect.width() - (title.getWidth() + (iconDrawable != null ? dp(4): 0)) - iconWithMarginPx) / 2f;
        if (animatedEmojiDrawable != null) {
            final int top = (int) (rect.centerY() - dp(20) / 2f);
            animatedEmojiDrawable.setBounds(
                (int) titleX,
                top,
                (int) (titleX) + dp(20),
                top + dp(20)
            );
            animatedEmojiDrawable.setAlpha(isLocked ? 128 : 255);
            animatedEmojiDrawable.draw(canvas);
            titleX += iconWithMarginPx;
        } else if (iconDrawable != null) {
            final int top = (int) (rect.centerY() - dp(24) / 2f);
            iconDrawable.setBounds(
                    (int) titleX,
                    top,
                    (int) (titleX) + dp(24),
                    top + dp(24)
            );
            iconDrawable.setAlpha(isLocked ? 128 : 255);
            iconDrawable.draw(canvas);
            titleX += iconWithMarginPx;
        }
        title.ellipsize(Math.max(1, rect.width() - dp(15) - iconWithMarginPx));
        title.draw(canvas, titleX, rect.centerY(), isLocked ? 0.5f: 1f);
        canvas.restore();

        if (buttonCustom != null) {
            if (isLocked) {
                final Drawable drawable = Theme.getThemeDrawable(Theme.key_drawable_botLock, resourcesProvider);
                final int x = (int) rect.right - dp(3) - drawable.getIntrinsicWidth();
                BaseCell.setDrawableBounds(drawable, x, rect.top + dp(3));
                drawable.draw(canvas);
            }

        } else if (button instanceof TLRPC.TL_keyboardButtonWebView) {
            final Drawable drawable = Theme.getThemeDrawable(Theme.key_drawable_botWebView, resourcesProvider);
            final int x = (int) rect.right - dp(3) - drawable.getIntrinsicWidth();
            BaseCell.setDrawableBounds(drawable, x, rect.top + dp(3));
            drawable.draw(canvas);
        } else if (button instanceof TLRPC.TL_keyboardButtonUrl) {
            final Drawable drawable;
            if (LinkManager.isWebAppLink(button.url)) {
                drawable = Theme.getThemeDrawable(Theme.key_drawable_botWebView, resourcesProvider);
            } else if (isInviteButton) {
                drawable = Theme.getThemeDrawable(Theme.key_drawable_botInvite, resourcesProvider);
            } else {
                drawable = Theme.getThemeDrawable(Theme.key_drawable_botLink, resourcesProvider);
            }
            int x = (int) rect.right - dp(3) - drawable.getIntrinsicWidth();
            BaseCell.setDrawableBounds(drawable, x, rect.top + dp(3));
            drawable.draw(canvas);
        } else if (button instanceof TLRPC.TL_keyboardButtonSwitchInline || button instanceof TLRPC.TL_keyboardButtonRequestPeer) {
            final Drawable drawable = Theme.getThemeDrawable(Theme.key_drawable_botInline, resourcesProvider);
            final int x = (int) rect.right - dp(3) - drawable.getIntrinsicWidth();
            BaseCell.setDrawableBounds(drawable, x, rect.top + dp(3));
            drawable.draw(canvas);
        } else if (button instanceof TLRPC.TL_keyboardButtonBuy && drawBuyCard) {
            final int x = (int) rect.right - dp(5) - Theme.chat_botCardDrawable.getIntrinsicWidth();
            BaseCell.setDrawableBounds(Theme.chat_botCardDrawable, x, rect.top + dp(4));
            Theme.chat_botCardDrawable.draw(canvas);
        }

        canvas.restore();

        return invalidate;
    }

    public void setPressed(boolean pressed) {
        if (this.pressed != pressed) {
            this.pressed = pressed;
            invalidateRunnable.run();
            if (pressed) {
                if (pressAnimator != null) {
                    pressAnimator.removeAllListeners();
                    pressAnimator.cancel();
                }
            }
            if (!pressed && pressT != 0) {
                pressAnimator = ValueAnimator.ofFloat(pressT, 0);
                pressAnimator.addUpdateListener(animation -> {
                    pressT = (float) animation.getAnimatedValue();
                    invalidateRunnable.run();
                });
                pressAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        pressAnimator = null;
                    }
                });
                pressAnimator.setInterpolator(new OvershootInterpolator(2.0f));
                pressAnimator.setDuration(350);
                pressAnimator.start();
            }
        }
    }

    public boolean hasPositionFlag(int flag) {
        return (positionFlags & flag) == flag;
    }

    public float getPressScale() {
        if (pressed && pressT != 1f) {
            pressT += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / 100f;
            pressT = Utilities.clamp(pressT, 1f, 0);
            invalidateRunnable.run();
        }
        return 0.96f + 0.04f * (1f - pressT);
    }
}

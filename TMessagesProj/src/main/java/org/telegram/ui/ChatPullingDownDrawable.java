package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CounterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LetterDrawable;
import org.telegram.ui.Components.StaticLayoutEx;

import java.util.ArrayList;

public class ChatPullingDownDrawable implements NotificationCenter.NotificationCenterDelegate {

    public int dialogFolderId;
    public int dialogFilterId;
    int lastWidth;
    float circleRadius;

    Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Path path = new Path();

    StaticLayout chatNameLayout;
    StaticLayout layout1;
    StaticLayout layout2;

    int chatNameWidth;
    int layout1Width;
    int layout2Width;

    private final ImageReceiver imageReceiver;
    TLRPC.Chat nextChat;
    TLRPC.TL_forumTopic nextTopic;
    private long lastWidthTopicId = 0L;

    AnimatorSet showReleaseAnimator;

    float swipeToReleaseProgress;
    float bounceProgress;

    public boolean animateSwipeToRelease;
    boolean animateCheck;
    float checkProgress;
    long lastHapticTime;
    float lastProgress;
    boolean emptyStub;
    float progressToBottomPanel;
    private final View fragmentView;
    public long lastShowingReleaseTime;

    boolean recommendedChannel;
    boolean drawFolderBackground;
    private final boolean isTopic;
    Runnable onAnimationFinishRunnable;
    public long nextDialogId;
    View parentView;

    private boolean visibleCounterDrawable = true;
    CounterView.CounterDrawable counterDrawable = new CounterView.CounterDrawable(null, true, null);
    int params[] = new int[3];
    private final int currentAccount;
    private final int folderId;
    private final int filterId;
    private final long topicId;
    private final long currentDialog;
    private final Theme.ResourcesProvider resourcesProvider;
    private AnimatedEmojiDrawable animatedEmojiDrawable;

    public ChatPullingDownDrawable(int currentAccount, View fragmentView, long currentDialog, int folderId, int filterId, long topicId, Theme.ResourcesProvider resourcesProvider) {
        this.fragmentView = fragmentView;
        this.currentAccount = currentAccount;
        this.currentDialog = currentDialog;
        this.folderId = folderId;
        this.filterId = filterId;
        this.topicId = topicId;
        this.isTopic = MessagesController.getInstance(currentAccount).isForum(currentDialog);
        this.resourcesProvider = resourcesProvider;
        this.imageReceiver = new ImageReceiver(fragmentView);

        arrowPaint.setStrokeWidth(AndroidUtilities.dpf2(2.8f));
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        counterDrawable.gravity = Gravity.LEFT;
        counterDrawable.setType(CounterView.CounterDrawable.TYPE_CHAT_PULLING_DOWN);
        counterDrawable.addServiceGradient = true;
        counterDrawable.circlePaint = getThemedPaint(Theme.key_paint_chatActionBackground);
        counterDrawable.textPaint = textPaint;

        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint2.setTextSize(AndroidUtilities.dp(14));

        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void updateDialog(TLRPC.Chat chat) {
        if (chat == null) {
            updateDialog();
            return;
        }

        nextDialogId = -chat.id;
        drawFolderBackground = params[0] == 1;
        dialogFolderId = params[1];
        dialogFilterId = params[2];
        emptyStub = false;
        nextChat = chat;
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(currentAccount, nextChat);
        imageReceiver.setImage(ImageLocation.getForChat(nextChat, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, null, UserConfig.getInstance(0).getCurrentUser(), 0);
        MessagesController.getInstance(currentAccount).ensureMessagesLoaded(-chat.id, 0, null);

        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).getDialog(-chat.id);
        final int count = dialog == null ? 0 : dialog.unread_count;
        counterDrawable.setCount(count, false);
        visibleCounterDrawable = count > 0;

        recommendedChannel = true;
        nextTopic = null;
    }

    public void updateDialog() {
        recommendedChannel = false;
        nextTopic = null;
        TLRPC.Dialog dialog = getNextUnreadDialog(currentDialog, folderId, filterId, true, params);
        if (dialog != null) {
            nextDialogId = dialog.id;
            drawFolderBackground = params[0] == 1;
            dialogFolderId = params[1];
            dialogFilterId = params[2];
            emptyStub = false;
            nextChat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
            if (nextChat == null) {
                nextChat = MessagesController.getInstance(currentAccount).getChat(dialog.id);
            }
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(currentAccount, nextChat);
            imageReceiver.setImage(ImageLocation.getForChat(nextChat, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, null, UserConfig.getInstance(0).getCurrentUser(), 0);
            MessagesController.getInstance(currentAccount).ensureMessagesLoaded(dialog.id, 0, null);
            final int count = dialog.unread_count;
            counterDrawable.setCount(count, false);
            visibleCounterDrawable = count > 0;
        } else {
            nextChat = null;
            drawFolderBackground = false;
            emptyStub = true;
        }
    }

    public void updateTopic() {
        recommendedChannel = false;
        drawFolderBackground = false;
        nextChat = null;
        nextDialogId = 0;
        imageReceiver.clearImage();

        TLRPC.TL_forumTopic topic = getNextUnreadTopic(-currentDialog);
        if (topic != null) {
            emptyStub = false;
            nextTopic = topic;
            Drawable forumIcon;
            if (topic.id == 1) {
                if (parentView != null && animatedEmojiDrawable != null) {
                    animatedEmojiDrawable.removeView(parentView);
                }
                animatedEmojiDrawable = null;
                forumIcon = ForumUtilities.createGeneralTopicDrawable(fragmentView.getContext(), 1f, getThemedColor(Theme.key_chat_inMenu), false, true);
                imageReceiver.setImageBitmap(forumIcon);
            } else if (topic.icon_emoji_id != 0) {
                if (animatedEmojiDrawable == null || animatedEmojiDrawable.getDocumentId() != topic.icon_emoji_id) {
                    if (animatedEmojiDrawable != null && parentView != null) {
                        animatedEmojiDrawable.removeView(parentView);
                    }
                    animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC_PULL_DOWN, currentAccount, topic.icon_emoji_id);
                    animatedEmojiDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_serviceText), PorterDuff.Mode.SRC_IN));
                }
                if (animatedEmojiDrawable != null && parentView != null) {
                    animatedEmojiDrawable.addView(parentView);
                }
                imageReceiver.setImageBitmap((Bitmap) null);
            } else {
                if (parentView != null && animatedEmojiDrawable != null) {
                    animatedEmojiDrawable.removeView(parentView);
                }
                animatedEmojiDrawable = null;
                forumIcon = ForumUtilities.createTopicDrawable(topic, false);
                imageReceiver.setImageBitmap(forumIcon);
            }
            final int count = topic.unread_count;
            counterDrawable.setCount(count, false);
            visibleCounterDrawable = count > 0;
        } else {
            nextTopic = null;
            emptyStub = true;
        }
    }

    public void setWidth(int width) {
        if (width != lastWidth || (isTopic && nextTopic != null && lastWidthTopicId != nextTopic.id)) {
            circleRadius = AndroidUtilities.dp(56) / 2f;
            lastWidth = width;

            CharSequence nameStr;
            if (nextChat != null) {
                nameStr = nextChat.title;
            } else if (nextTopic != null) {
                nameStr = nextTopic.title;
            } else if (isTopic) {
                String forumName = MessagesController.getInstance(currentAccount).getChat(-currentDialog).title;
                nameStr = LocaleController.formatString(R.string.SwipeToGoNextTopicEnd, forumName);
            } else {
                nameStr = LocaleController.getString(R.string.SwipeToGoNextChannelEnd);
            }
            chatNameWidth = (int) textPaint.measureText(nameStr, 0, nameStr.length());
            chatNameWidth = Math.min(chatNameWidth, lastWidth - AndroidUtilities.dp(60));
            chatNameLayout = StaticLayoutEx.createStaticLayout(nameStr, textPaint, chatNameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, chatNameWidth, 1);

            String str1 = null;
            String str2 = null;

            if (recommendedChannel) {
                str1 = LocaleController.getString(R.string.SwipeToGoNextRecommendedChannel);
                str2 = LocaleController.getString(R.string.ReleaseToGoNextRecommendedChannel);
            } else if (isTopic) {
                str1 = LocaleController.getString(R.string.SwipeToGoNextUnreadTopic);
                str2 = LocaleController.getString(R.string.ReleaseToGoNextUnreadTopic);
            } else if (drawFolderBackground && dialogFolderId != folderId && dialogFolderId != 0) {
                str1 = LocaleController.getString(R.string.SwipeToGoNextArchive);
                str2 = LocaleController.getString(R.string.ReleaseToGoNextArchive);
            } else if (drawFolderBackground) {
                str1 = LocaleController.getString(R.string.SwipeToGoNextFolder);
                str2 = LocaleController.getString(R.string.ReleaseToGoNextFolder);
            } else {
                str1 = LocaleController.getString(R.string.SwipeToGoNextChannel);
                str2 = LocaleController.getString(R.string.ReleaseToGoNextChannel);
            }
            layout1Width = (int) textPaint2.measureText(str1);
            layout1Width = Math.min(layout1Width, lastWidth - AndroidUtilities.dp(60));
            layout1 = new StaticLayout(str1, textPaint2, layout1Width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);


            layout2Width = (int) textPaint2.measureText(str2);
            layout2Width = Math.min(layout2Width, lastWidth - AndroidUtilities.dp(60));
            layout2 = new StaticLayout(str2, textPaint2, layout2Width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);


            float cx = lastWidth / 2f;
            float cy = AndroidUtilities.dp(12) + circleRadius;
            imageReceiver.setImageCoords(cx - AndroidUtilities.dp(40) / 2f, cy - AndroidUtilities.dp(40) / 2f, AndroidUtilities.dp(40), AndroidUtilities.dp(40));
            imageReceiver.setRoundRadius((int) (AndroidUtilities.dp(40) / 2f));

            counterDrawable.setSize(AndroidUtilities.dp(28), AndroidUtilities.dp(100));
            if (isTopic) {
                lastWidthTopicId = nextTopic == null ? 0 : nextTopic.id;
            }
        }

    }

    public void draw(Canvas canvas, View parent, float progress, float alpha) {
        if (this.parentView != parent) {
            this.parentView = parent;
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.addView(parent);
            }
        }
        counterDrawable.setParent(parent);
        int oldAlpha, oldAlpha1, oldAlpha2, oldAlpha3;
        float offset = AndroidUtilities.dp(110) * progress;
        if (offset < AndroidUtilities.dp(8)) {
            return;
        }

        if (progress < 0.2f) {
            alpha *= progress * 5f;
        }
        Theme.applyServiceShaderMatrix(lastWidth, parent.getMeasuredHeight(), 0, parent.getMeasuredHeight() - offset);

        textPaint.setColor(getThemedColor(Theme.key_chat_serviceText));
        arrowPaint.setColor(getThemedColor(Theme.key_chat_serviceText));
        textPaint2.setColor(getThemedColor(Theme.key_chat_messagePanelHint));

        oldAlpha = getThemedPaint(Theme.key_paint_chatActionBackground).getAlpha();
        oldAlpha1 = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
        oldAlpha2 = textPaint.getAlpha();
        oldAlpha3 = arrowPaint.getAlpha();
        Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (oldAlpha1 * alpha));
        getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha((int) (oldAlpha * alpha));
        textPaint.setAlpha((int) (oldAlpha2 * alpha));

        if ((progress >= 1f && lastProgress < 1f) || (progress < 1f && lastProgress == 1f)) {
            long time = System.currentTimeMillis();
            if (time - lastHapticTime > 100) {
                parent.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                lastHapticTime = time;
            }
            lastProgress = progress;
        }

        if (progress == 1f && !animateSwipeToRelease) {
            animateSwipeToRelease = true;
            animateCheck = true;
            showReleaseState(true, parent);
            lastShowingReleaseTime = System.currentTimeMillis();
        } else if (progress != 1f && animateSwipeToRelease) {
            animateSwipeToRelease = false;
            showReleaseState(false, parent);
        }

        float cx = lastWidth / 2f;
        float bounceOffset = bounceProgress * -AndroidUtilities.dp(4);

        if (emptyStub) {
            offset -= bounceOffset;
        }


        float widthRadius = Math.max(0, Math.min(circleRadius, offset / 2f - AndroidUtilities.dp(16) * progress - AndroidUtilities.dp(4)));
        float widthRadius2 = Math.max(0, Math.min(circleRadius * progress, offset / 2f - AndroidUtilities.dp(8) * progress));
        float size = (widthRadius2 * 2 - AndroidUtilities.dp2(16)) * (1f - swipeToReleaseProgress) + AndroidUtilities.dp(56) * swipeToReleaseProgress;

        if (swipeToReleaseProgress < 1f || emptyStub) {
            float bottom = -AndroidUtilities.dp(8) * (1f - swipeToReleaseProgress) + (-offset + AndroidUtilities.dp(56)) * swipeToReleaseProgress;
            AndroidUtilities.rectTmp.set(cx - widthRadius, -offset, cx + widthRadius, bottom);
            float bgAlpha = 1f;
            if (swipeToReleaseProgress > 0 && !emptyStub) {
                float inset = AndroidUtilities.dp(16) * swipeToReleaseProgress;
                AndroidUtilities.rectTmp.inset(inset, inset);
                bgAlpha = 1 - swipeToReleaseProgress;
            }
            drawBackground(canvas, AndroidUtilities.rectTmp, bgAlpha);

            float arrowCy = -offset + AndroidUtilities.dp(24) + AndroidUtilities.dp(8) * (1f - progress) - AndroidUtilities.dp(36) * swipeToReleaseProgress;
            canvas.save();
            AndroidUtilities.rectTmp.inset(AndroidUtilities.dp(1), AndroidUtilities.dp(1));
            canvas.clipRect(AndroidUtilities.rectTmp);
            if (swipeToReleaseProgress > 0f) {
                arrowPaint.setAlpha((int) ((1f - swipeToReleaseProgress) * 255));
            }
            drawArrow(canvas, cx, arrowCy, AndroidUtilities.dp(24) * progress);

            if (emptyStub) {
                float top = (-AndroidUtilities.dp(8) - AndroidUtilities.dp2(8) * progress - size) * (1f - swipeToReleaseProgress) + (-offset - AndroidUtilities.dp(2)) * swipeToReleaseProgress + bounceOffset;
                arrowPaint.setAlpha(oldAlpha3);
                canvas.save();
                canvas.scale(progress, progress, cx, top + AndroidUtilities.dp(28));
                drawCheck(canvas, cx, top + AndroidUtilities.dp(28));
                canvas.restore();
            }
            canvas.restore();
        }

        if (chatNameLayout != null && swipeToReleaseProgress > 0) {
            getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha((int) (oldAlpha * alpha));
            textPaint.setAlpha((int) (oldAlpha2 * alpha));

            float y = AndroidUtilities.dp(20) * (1f - swipeToReleaseProgress) - AndroidUtilities.dp(36) * swipeToReleaseProgress + bounceOffset;
            AndroidUtilities.rectTmp.set((lastWidth - chatNameWidth) / 2f, y, lastWidth - (lastWidth - chatNameWidth) / 2f, y + chatNameLayout.getHeight());
            AndroidUtilities.rectTmp.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(15), AndroidUtilities.dp(15), getThemedPaint(Theme.key_paint_chatActionBackground));
            if (hasGradientService()) {
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(15), AndroidUtilities.dp(15), Theme.chat_actionBackgroundGradientDarkenPaint);
            }

            canvas.save();
            canvas.translate((lastWidth - chatNameWidth) / 2f, y);
            chatNameLayout.draw(canvas);
            canvas.restore();
        }

        if (!emptyStub && size > 0) {
            float top = (-AndroidUtilities.dp(8) - AndroidUtilities.dp2(8) * progress - size) * (1f - swipeToReleaseProgress) + (-offset + AndroidUtilities.dp(4)) * swipeToReleaseProgress + bounceOffset;
            ImageReceiver finalImageReceiver;
            if (animatedEmojiDrawable != null && animatedEmojiDrawable.getImageReceiver() != null) {
                finalImageReceiver = animatedEmojiDrawable.getImageReceiver();
            } else {
                finalImageReceiver = imageReceiver;
            }
            finalImageReceiver.setAlpha(alpha);
            finalImageReceiver.setRoundRadius((int) (size / 2f));
            finalImageReceiver.setImageCoords(cx - size / 2f, top, size, size);
            if (isTopic && finalImageReceiver.getDrawable() != null && finalImageReceiver.getDrawable() instanceof CombinedDrawable && ((CombinedDrawable) finalImageReceiver.getDrawable()).getIcon() instanceof LetterDrawable) {
                ((LetterDrawable) ((CombinedDrawable) finalImageReceiver.getDrawable()).getIcon()).scale = progress;
            }

            if (swipeToReleaseProgress > 0 && visibleCounterDrawable) {
                canvas.saveLayerAlpha(finalImageReceiver.getImageX(), finalImageReceiver.getImageY(), finalImageReceiver.getImageX() + finalImageReceiver.getImageWidth(), finalImageReceiver.getImageY() + finalImageReceiver.getImageHeight(), 255, Canvas.ALL_SAVE_FLAG);
                finalImageReceiver.draw(canvas);
                canvas.scale(swipeToReleaseProgress, swipeToReleaseProgress, cx + AndroidUtilities.dp(12) + counterDrawable.getCenterX(), top - AndroidUtilities.dp(6) + AndroidUtilities.dp(14));
                canvas.translate(cx + AndroidUtilities.dp(12), top - AndroidUtilities.dp(6));
                counterDrawable.updateBackgroundRect();
                counterDrawable.rectF.inset(-AndroidUtilities.dp(2), -AndroidUtilities.dp(2));
                canvas.drawRoundRect(counterDrawable.rectF, counterDrawable.rectF.height() / 2f, counterDrawable.rectF.height() / 2f, xRefPaint);
                canvas.restore();

                canvas.save();
                canvas.scale(swipeToReleaseProgress, swipeToReleaseProgress, cx + AndroidUtilities.dp(12) + counterDrawable.getCenterX(), top - AndroidUtilities.dp(6) + AndroidUtilities.dp(14));
                canvas.translate(cx + AndroidUtilities.dp(12), top - AndroidUtilities.dp(6));
                counterDrawable.draw(canvas);
                canvas.restore();
            } else {
                finalImageReceiver.draw(canvas);
            }
            finalImageReceiver.setAlpha(1f);
        }

        getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha(oldAlpha);
        Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha1);
        textPaint.setAlpha(oldAlpha2);
        arrowPaint.setAlpha(oldAlpha3);
    }

    private void drawCheck(Canvas canvas, float cx, float cy) {
        if (!animateCheck) {
            return;
        }
        if (checkProgress < 1f) {
            checkProgress += 16 / 220f;
            if (checkProgress > 1f) {
                checkProgress = 1f;
            }
        }
        float p1 = checkProgress > 0.5f ? 1f : checkProgress / 0.5f;
        float p2 = checkProgress < 0.5f ? 0 : (checkProgress - 0.5f) / 0.5f;
        canvas.save();
        canvas.clipRect(AndroidUtilities.rectTmp);
        canvas.translate(cx - AndroidUtilities.dp(24), cy - AndroidUtilities.dp(24));
        float x1 = AndroidUtilities.dp(16);
        float y1 = AndroidUtilities.dp(26);
        float x2 = AndroidUtilities.dp(22);
        float y2 = AndroidUtilities.dp(32);
        float x3 = AndroidUtilities.dp(32);
        float y3 = AndroidUtilities.dp(20);
        canvas.drawLine(x1, y1, x1 * (1f - p1) + x2 * p1, y1 * (1f - p1) + y2 * p1, arrowPaint);
        if (p2 > 0) {
            canvas.drawLine(x2, y2, x2 * (1f - p2) + x3 * p2, y2 * (1f - p2) + y3 * p2, arrowPaint);
        }
        canvas.restore();
    }

    private void drawBackground(Canvas canvas, RectF rectTmp, float alpha) {
        if (drawFolderBackground) {
            path.reset();
            float roundRadius = rectTmp.width() * 0.2f;
            float folderOffset = rectTmp.width() * 0.1f;
            float folderOffset2 = rectTmp.width() * 0.03f;
            float roundRadius2 = folderOffset / 2f;

            float h = rectTmp.height() - folderOffset;

            path.moveTo(rectTmp.right, rectTmp.top + roundRadius + folderOffset);
            path.rQuadTo(0, -roundRadius, -roundRadius, -roundRadius);
            path.rLineTo(-(rectTmp.width() - (2 * roundRadius)) / 2 + roundRadius2 * 2 - folderOffset2, 0);
            path.rQuadTo(-roundRadius2 / 2, 0, -roundRadius2 * 2, -folderOffset / 2);
            path.rQuadTo(-roundRadius2 / 2, -folderOffset / 2, -roundRadius2 * 2, -folderOffset / 2);
            path.rLineTo(-(rectTmp.width() - (2 * roundRadius)) / 2 + roundRadius2 * 2 + folderOffset2, 0);
            path.rQuadTo(-roundRadius, 0, -roundRadius, roundRadius);
            path.rLineTo(0, (h + folderOffset - (2 * roundRadius)));
            path.rQuadTo(0, roundRadius, roundRadius, roundRadius);
            path.rLineTo((rectTmp.width() - (2 * roundRadius)), 0);
            path.rQuadTo(roundRadius, 0, roundRadius, -roundRadius);
            path.rLineTo(0, -(h - (2 * roundRadius)));
            path.close();
            canvas.drawPath(path, getThemedPaint(Theme.key_paint_chatActionBackground));
            if (hasGradientService()) {
                canvas.drawPath(path, Theme.chat_actionBackgroundGradientDarkenPaint);
            }
        } else {
            int oldAlpha1 = getThemedPaint(Theme.key_paint_chatActionBackground).getAlpha();
            getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha((int) (oldAlpha1 * alpha));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, circleRadius, circleRadius, getThemedPaint(Theme.key_paint_chatActionBackground));
            getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha(oldAlpha1);
            if (hasGradientService()) {
                int oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
                Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (oldAlpha2 * alpha));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, circleRadius, circleRadius, Theme.chat_actionBackgroundGradientDarkenPaint);
                Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha2);
            }
        }
    }

    private void showReleaseState(boolean show, View parent) {
        if (showReleaseAnimator != null) {
            showReleaseAnimator.removeAllListeners();
            showReleaseAnimator.cancel();
        }
        if (show) {
            ValueAnimator out = ValueAnimator.ofFloat(swipeToReleaseProgress, 1f);
            out.addUpdateListener(animation -> {
                swipeToReleaseProgress = (float) animation.getAnimatedValue();
                parent.invalidate();
                fragmentView.invalidate();
            });

            out.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            out.setDuration(250);

            bounceProgress = 0;

            ValueAnimator bounceUp = ValueAnimator.ofFloat(0f, 1f);
            bounceUp.addUpdateListener(animation -> {
                bounceProgress = (float) animation.getAnimatedValue();
                parent.invalidate();
            });

            bounceUp.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            bounceUp.setDuration(180);

            ValueAnimator bounceDown = ValueAnimator.ofFloat(1f, -0.5f);
            bounceDown.addUpdateListener(animation -> {
                bounceProgress = (float) animation.getAnimatedValue();
                parent.invalidate();
            });

            bounceDown.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            bounceDown.setDuration(120);

            ValueAnimator bounceOut = ValueAnimator.ofFloat(-0.5f, 0f);
            bounceOut.addUpdateListener(animation -> {
                bounceProgress = (float) animation.getAnimatedValue();
                parent.invalidate();
            });

            bounceOut.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            bounceOut.setDuration(100);

            showReleaseAnimator = new AnimatorSet();
            showReleaseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    bounceProgress = 0f;
                    swipeToReleaseProgress = 1f;
                    parent.invalidate();
                    fragmentView.invalidate();
                    if (onAnimationFinishRunnable != null) {
                        onAnimationFinishRunnable.run();
                        onAnimationFinishRunnable = null;
                    }
                }
            });

            AnimatorSet bounce = new AnimatorSet();
            bounce.playSequentially(bounceUp, bounceDown, bounceOut);

            showReleaseAnimator.playTogether(out, bounce);
            showReleaseAnimator.start();
        } else {
            ValueAnimator out = ValueAnimator.ofFloat(swipeToReleaseProgress, 0f);
            out.addUpdateListener(animation -> {
                swipeToReleaseProgress = (float) animation.getAnimatedValue();
                fragmentView.invalidate();
                parent.invalidate();
            });

            out.setInterpolator(CubicBezierInterpolator.DEFAULT);
            out.setDuration(220);
            showReleaseAnimator = new AnimatorSet();
            showReleaseAnimator.playTogether(out);
            showReleaseAnimator.start();
        }
    }

    private void drawArrow(Canvas canvas, float cx, float cy, float size) {
        canvas.save();
        float s = size / AndroidUtilities.dpf2(24);
        canvas.scale(s, s, cx, cy - AndroidUtilities.dp(20));
        canvas.translate(cx - AndroidUtilities.dp2(12), cy - AndroidUtilities.dp(12));
        canvas.drawLine(AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(4f), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(22), arrowPaint);
        canvas.drawLine(AndroidUtilities.dpf2(3.5f), AndroidUtilities.dpf2(12), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(3.5f), arrowPaint);
        canvas.drawLine(AndroidUtilities.dpf2(25 - 3.5f), AndroidUtilities.dpf2(12), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(3.5f), arrowPaint);
        canvas.restore();
    }

    public void onAttach() {
        imageReceiver.onAttachedToWindow();
        if (animatedEmojiDrawable != null && parentView != null) {
            animatedEmojiDrawable.addView(parentView);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
    }

    public void onDetach() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        imageReceiver.onDetachedFromWindow();
        if (animatedEmojiDrawable != null && parentView != null) {
            animatedEmojiDrawable.removeView(parentView);
        }
        lastProgress = 0;
        lastHapticTime = 0;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (nextDialogId != 0) {
            TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(nextDialogId);
            if (dialog != null) {
                final int count = dialog.unread_count;
                counterDrawable.setCount(count, true);
                visibleCounterDrawable = count > 0;
                if (parentView != null) {
                    parentView.invalidate();
                }
            }
        }
    }


    public static TLRPC.Dialog getNextUnreadDialog(long currentDialogId, int folderId, int filterId) {
        return getNextUnreadDialog(currentDialogId, folderId, filterId, true, null);
    }

    public static TLRPC.Dialog getNextUnreadDialog(long currentDialogId, int folderId, int filterId, boolean searchNext, int[] params) {
        MessagesController messagesController = AccountInstance.getInstance(UserConfig.selectedAccount).getMessagesController();
        if (params != null) {
            params[0] = 0;
            params[1] = folderId;
            params[2] = filterId;
        }
        ArrayList<TLRPC.Dialog> dialogs;
        if (filterId != 0) {
            MessagesController.DialogFilter filter = messagesController.dialogFiltersById.get(filterId);
            if (filter == null) {
                return null;
            }
            dialogs = filter.dialogs;
        } else {
            dialogs = messagesController.getDialogs(folderId);
        }
        if (dialogs == null) {
            return null;
        }
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            TLRPC.Chat chat = messagesController.getChat(-dialog.id);
            if (chat != null && dialog.id != currentDialogId && dialog.unread_count > 0 && DialogObject.isChannel(dialog) && !chat.megagroup && !messagesController.isPromoDialog(dialog.id, false)) {
                String reason = messagesController.getRestrictionReason(chat.restriction_reason);
                if (reason == null) {
                    return dialog;
                }
            }
        }

        if (searchNext) {
            if (filterId != 0) {
                for (int i = 0; i < messagesController.dialogFilters.size(); i++) {
                    int newFilterId = messagesController.dialogFilters.get(i).id;
                    if (filterId != newFilterId) {
                        TLRPC.Dialog dialog = getNextUnreadDialog(currentDialogId, folderId, newFilterId, false, params);
                        if (dialog != null) {
                            if (params != null) {
                                params[0] = 1;
                            }
                            return dialog;
                        }
                    }
                }
            }

            for (int i = 0; i < messagesController.dialogsByFolder.size(); i++) {
                int newFolderId = messagesController.dialogsByFolder.keyAt(i);
                if (folderId != newFolderId) {
                    TLRPC.Dialog dialog = getNextUnreadDialog(currentDialogId, newFolderId, 0, false, params);
                    if (dialog != null) {
                        if (params != null) {
                            params[0] = 1;
                        }
                        return dialog;
                    }
                }
            }
        }

        return null;
    }

    private TLRPC.TL_forumTopic getNextUnreadTopic(long currentDialogId) {
        ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(currentDialogId);
        TLRPC.TL_forumTopic nextUnreadTopic = null;
        if (topics != null && topics.size() > 1) {
            for (int i = 0; i < topics.size(); i++) {
                TLRPC.TL_forumTopic topic = topics.get(i);
                if (topic.id != topicId && !topic.hidden && topic.unread_count > 0 && (nextUnreadTopic == null || topic.topMessage != null && nextUnreadTopic.topMessage != null && topic.topMessage.date > nextUnreadTopic.topMessage.date)) {
                    nextUnreadTopic = topic;
                }
            }
        }
        return nextUnreadTopic;
    }

    public long getChatId() {
        if (nextChat == null) return 0;
        return nextChat.id;
    }

    public TLRPC.TL_forumTopic getTopic() {
        return nextTopic;
    }

    public void drawBottomPanel(Canvas canvas, int top, int bottom, int width) {
        if (showBottomPanel && progressToBottomPanel != 1f) {
            progressToBottomPanel += 16f / 150f;
            if (progressToBottomPanel > 1f) {
                progressToBottomPanel = 1f;
            } else {
                fragmentView.invalidate();
            }
        } else if (!showBottomPanel && progressToBottomPanel != 0) {
            progressToBottomPanel -= 16f / 150f;
            if (progressToBottomPanel < 0) {
                progressToBottomPanel = 0;
            } else {
                fragmentView.invalidate();
            }
        }

        textPaint2.setColor(getThemedColor(Theme.key_chat_messagePanelHint));
        Paint composeBackgroundPaint = getThemedPaint(Theme.key_paint_chatComposeBackground);
        int oldAlpha = composeBackgroundPaint.getAlpha();
        int oldAlphaText = textPaint2.getAlpha();

        composeBackgroundPaint.setAlpha((int) (oldAlpha * progressToBottomPanel));
        canvas.drawRect(0, top, width, bottom, composeBackgroundPaint);

        if (layout1 != null && swipeToReleaseProgress < 1f) {
            textPaint2.setAlpha((int) (oldAlphaText * (1f - swipeToReleaseProgress) * progressToBottomPanel));
            float y = top + (bottom - top - layout1.getHeight()) / 2f - AndroidUtilities.dp(10) * swipeToReleaseProgress;
            canvas.save();
            canvas.translate((lastWidth - layout1Width) / 2f, y);
            layout1.draw(canvas);
            canvas.restore();
        }

        if (layout2 != null && swipeToReleaseProgress > 0) {
            textPaint2.setAlpha((int) (oldAlphaText * swipeToReleaseProgress * progressToBottomPanel));
            float y = top + (bottom - top - layout2.getHeight()) / 2f + AndroidUtilities.dp(10) * (1f - swipeToReleaseProgress);
            canvas.save();
            canvas.translate((lastWidth - layout2Width) / 2f, y);
            layout2.draw(canvas);
            canvas.restore();
        }

        textPaint2.setAlpha(oldAlphaText);
        composeBackgroundPaint.setAlpha(oldAlpha);
    }

    boolean showBottomPanel;

    public void showBottomPanel(boolean b) {
        showBottomPanel = b;
        fragmentView.invalidate();
    }

    public boolean needDrawBottomPanel() {
        return (showBottomPanel || progressToBottomPanel > 0) && !emptyStub;
    }

    public boolean animationIsRunning() {
        return swipeToReleaseProgress != 1f;
    }

    public void runOnAnimationFinish(Runnable runnable) {
        if (showReleaseAnimator != null) {
            showReleaseAnimator.removeAllListeners();
            showReleaseAnimator.cancel();
        }
        onAnimationFinishRunnable = runnable;
        showReleaseAnimator = new AnimatorSet();

        ValueAnimator out = ValueAnimator.ofFloat(swipeToReleaseProgress, 1f);
        out.addUpdateListener(animation -> {
            swipeToReleaseProgress = (float) animation.getAnimatedValue();
            fragmentView.invalidate();
            if (parentView != null) {
                parentView.invalidate();
            }
        });
        ValueAnimator bounceOut = ValueAnimator.ofFloat(bounceProgress, 0f);
        bounceOut.addUpdateListener(animation -> {
            bounceProgress = (float) animation.getAnimatedValue();
            if (parentView != null) {
                parentView.invalidate();
            }
        });
        showReleaseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                bounceProgress = 0f;
                swipeToReleaseProgress = 1f;
                if (parentView != null) {
                    parentView.invalidate();
                }
                fragmentView.invalidate();
                if (onAnimationFinishRunnable != null) {
                    onAnimationFinishRunnable.run();
                    onAnimationFinishRunnable = null;
                }
            }
        });
        showReleaseAnimator.playTogether(out, bounceOut);
        showReleaseAnimator.setDuration(120);
        showReleaseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        showReleaseAnimator.start();
    }

    public void reset() {
        checkProgress = 0;
        animateCheck = false;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    private boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }
}

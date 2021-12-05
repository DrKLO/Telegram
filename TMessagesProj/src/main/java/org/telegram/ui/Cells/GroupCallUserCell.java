package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.WaveDrawable;

import java.util.ArrayList;

public class GroupCallUserCell extends FrameLayout {

    private AvatarWavesDrawable avatarWavesDrawable;

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView[] statusTextView = new SimpleTextView[5];
    private SimpleTextView fullAboutTextView;
    private RLottieImageView muteButton;
    private RLottieDrawable muteDrawable;
    private RLottieDrawable shakeHandDrawable;

    private RadialProgressView avatarProgressView;

    private AvatarDrawable avatarDrawable;

    private ChatObject.Call currentCall;
    private TLRPC.TL_groupCallParticipant participant;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;

    private Paint dividerPaint;

    private boolean lastMuted;
    private boolean lastRaisedHand;
    private int lastMuteColor;

    private AccountInstance accountInstance;

    private boolean needDivider;
    private boolean currentIconGray;
    private int currentStatus;
    private long selfId;

    private Runnable shakeHandCallback = () -> {
        shakeHandDrawable.setOnFinishCallback(null, 0);
        muteDrawable.setOnFinishCallback(null, 0);
        muteButton.setAnimation(muteDrawable);
    };

    private Runnable raiseHandCallback = () -> {
        int num = Utilities.random.nextInt(100);
        int endFrame;
        int startFrame;
        if (num < 32) {
            startFrame = 0;
            endFrame = 120;
        } else if (num < 64) {
            startFrame = 120;
            endFrame = 240;
        } else if (num < 97) {
            startFrame = 240;
            endFrame = 420;
        } else if (num == 98) {
            startFrame = 420;
            endFrame = 540;
        } else {
            startFrame = 540;
            endFrame = 720;
        }
        shakeHandDrawable.setCustomEndFrame(endFrame);
        shakeHandDrawable.setOnFinishCallback(shakeHandCallback, endFrame - 1);
        muteButton.setAnimation(shakeHandDrawable);
        shakeHandDrawable.setCurrentFrame(startFrame);
        muteButton.playAnimation();
    };

    private String grayIconColor = Theme.key_voipgroup_mutedIcon;

    private Runnable checkRaiseRunnable = () -> applyParticipantChanges(true, true);

    private Runnable updateRunnable = () -> {
        isSpeaking = false;
        applyParticipantChanges(true, true);
        avatarWavesDrawable.setAmplitude(0);
        updateRunnableScheduled = false;
    };
    private Runnable updateVoiceRunnable = () -> {
        applyParticipantChanges(true, true);
        updateVoiceRunnableScheduled = false;
    };
    private boolean updateRunnableScheduled;
    private boolean updateVoiceRunnableScheduled;
    private boolean isSpeaking;
    private boolean hasAvatar;

    private Drawable speakingDrawable;

    private AnimatorSet animatorSet;

    private float progressToAvatarPreview;

    public void setProgressToAvatarPreview(float progressToAvatarPreview) {
        this.progressToAvatarPreview = progressToAvatarPreview;
        nameTextView.setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(53) : -AndroidUtilities.dp(53)) * progressToAvatarPreview);

        if (isSelfUser() && progressToAvatarPreview > 0) {
            fullAboutTextView.setTranslationX((LocaleController.isRTL ? -AndroidUtilities.dp(53) : AndroidUtilities.dp(53)) * (1f - progressToAvatarPreview));
            fullAboutTextView.setVisibility(View.VISIBLE);
            fullAboutTextView.setAlpha(progressToAvatarPreview);
            statusTextView[4].setAlpha(1f - progressToAvatarPreview);
            statusTextView[4].setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(53) : -AndroidUtilities.dp(53)) * progressToAvatarPreview);
        } else {
            fullAboutTextView.setVisibility(View.GONE);

            for (int i = 0; i < statusTextView.length; i++) {
                if (!TextUtils.isEmpty(statusTextView[4].getText()) && statusTextView[4].getLineCount() > 1) {
                    statusTextView[i].setFullLayoutAdditionalWidth(AndroidUtilities.dp(92), LocaleController.isRTL ? AndroidUtilities.dp(48) : AndroidUtilities.dp(53));
                    statusTextView[i].setFullAlpha(progressToAvatarPreview);
                    statusTextView[i].setTranslationX(0);
                    statusTextView[i].invalidate();
                } else {
                    statusTextView[i].setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(53) : -AndroidUtilities.dp(53)) * progressToAvatarPreview);
                    statusTextView[i].setFullLayoutAdditionalWidth(0, 0);
                }
            }
        }

        avatarImageView.setAlpha(progressToAvatarPreview == 0 ? 1f : 0);
        avatarWavesDrawable.setShowWaves(isSpeaking && progressToAvatarPreview == 0, this);


        muteButton.setAlpha(1f - progressToAvatarPreview);
        muteButton.setScaleX(0.6f + 0.4f * (1f - progressToAvatarPreview));
        muteButton.setScaleY(0.6f + 0.4f * (1f - progressToAvatarPreview));

        invalidate();
    }

    public AvatarWavesDrawable getAvatarWavesDrawable() {
        return avatarWavesDrawable;
    }

    public void setUploadProgress(float progress, boolean animated) {
        avatarProgressView.setProgress(progress);
        if (progress < 1f) {
            AndroidUtilities.updateViewVisibilityAnimated(avatarProgressView, true, 1f, animated);
        } else {
            AndroidUtilities.updateViewVisibilityAnimated(avatarProgressView, false, 1f, animated);
        }
    }

    public void setDrawAvatar(boolean draw) {
        if (avatarImageView.getImageReceiver().getVisible() != draw) {
            avatarImageView.getImageReceiver().setVisible(draw, true);
        }
    }

    private static class VerifiedDrawable extends Drawable {

        private Drawable[] drawables = new Drawable[2];

        public VerifiedDrawable(Context context) {
            super();
            drawables[0] = context.getResources().getDrawable(R.drawable.verified_area).mutate();
            drawables[0].setColorFilter(new PorterDuffColorFilter(0xff75B3EE, PorterDuff.Mode.MULTIPLY));
            drawables[1] = context.getResources().getDrawable(R.drawable.verified_check).mutate();
        }

        @Override
        public int getIntrinsicWidth() {
            return drawables[0].getIntrinsicWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return drawables[0].getIntrinsicHeight();
        }

        @Override
        public void draw(Canvas canvas) {
            for (int a = 0; a < drawables.length; a++) {
                drawables[a].setBounds(getBounds());
                drawables[a].draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            for (int a = 0; a < drawables.length; a++) {
                drawables[a].setAlpha(alpha);
            }
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    public GroupCallUserCell(Context context) {
        super(context);

        dividerPaint = new Paint();
        dividerPaint.setColor(Theme.getColor(Theme.key_voipgroup_actionBar));

        avatarDrawable = new AvatarDrawable();
        setClipChildren(false);

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 11, 6, LocaleController.isRTL ? 11 : 0, 0));

        avatarProgressView = new RadialProgressView(context) {
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                paint.setColor(0x55000000);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (avatarImageView.getImageReceiver().hasNotThumb() && avatarImageView.getAlpha() > 0) {
                    paint.setAlpha((int) (0x55 * avatarImageView.getImageReceiver().getCurrentAlpha() * avatarImageView.getAlpha()));
                    canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                }
                avatarProgressView.setProgressColor(ColorUtils.setAlphaComponent(0xffffffff, (int) (255 * avatarImageView.getImageReceiver().getCurrentAlpha() * avatarImageView.getAlpha())));
                super.onDraw(canvas);
            }
        };
        avatarProgressView.setSize(AndroidUtilities.dp(26));
        avatarProgressView.setProgressColor(0xffffffff);
        avatarProgressView.setNoProgress(false);
        addView(avatarProgressView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 11, 6, LocaleController.isRTL ? 11 : 0, 0));
        AndroidUtilities.updateViewVisibilityAnimated(avatarProgressView, false, 1f, false);

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(16);
        nameTextView.setDrawablePadding(AndroidUtilities.dp(6));
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 54 : 67, 10, LocaleController.isRTL ? 67 : 54, 0));

        speakingDrawable = context.getResources().getDrawable(R.drawable.voice_volume_mini);
        speakingDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_voipgroup_speakingText), PorterDuff.Mode.MULTIPLY));

        for (int a = 0; a < statusTextView.length; a++) {
            int num = a;
            statusTextView[a] = new SimpleTextView(context) {

                float originalAlpha;

                @Override
                public void setAlpha(float alpha) {
                    originalAlpha = alpha;
                    float alphaOverride;
                    if (num == 4) {
                        alphaOverride = statusTextView[4].getFullAlpha();
                        if (isSelfUser() && progressToAvatarPreview > 0) {
                            super.setAlpha(1f - progressToAvatarPreview);
                        } else if (alphaOverride > 0) {
                            super.setAlpha(Math.max(alpha, alphaOverride));
                        } else {
                            super.setAlpha(alpha);
                        }
                    } else {
                        alphaOverride = 1.0f - statusTextView[4].getFullAlpha();
                        super.setAlpha(alpha * alphaOverride);
                    }
                }

                @Override
                public void setTranslationY(float translationY) {
                    if (num == 4 && getFullAlpha() > 0) {
                        translationY = 0;
                    }
                    super.setTranslationY(translationY);
                }

                @Override
                public float getAlpha() {
                    return originalAlpha;
                }

                @Override
                public void setFullAlpha(float value) {
                    super.setFullAlpha(value);
                    for (int a = 0; a < statusTextView.length; a++) {
                        statusTextView[a].setAlpha(statusTextView[a].getAlpha());
                    }
                }
            };
            statusTextView[a].setTextSize(15);
            statusTextView[a].setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            if (a == 4) {
                statusTextView[a].setBuildFullLayout(true);
                statusTextView[a].setTextColor(Theme.getColor(Theme.key_voipgroup_mutedIcon));
                addView(statusTextView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 54 : 67, 32, LocaleController.isRTL ? 67 : 54, 0));
            } else {
                if (a == 0) {
                    statusTextView[a].setTextColor(Theme.getColor(Theme.key_voipgroup_listeningText));
                    statusTextView[a].setText(LocaleController.getString("Listening", R.string.Listening));
                } else if (a == 1) {
                    statusTextView[a].setTextColor(Theme.getColor(Theme.key_voipgroup_speakingText));
                    statusTextView[a].setText(LocaleController.getString("Speaking", R.string.Speaking));
                    statusTextView[a].setDrawablePadding(AndroidUtilities.dp(2));
                } else if (a == 2) {
                    statusTextView[a].setTextColor(Theme.getColor(Theme.key_voipgroup_mutedByAdminIcon));
                    statusTextView[a].setText(LocaleController.getString("VoipGroupMutedForMe", R.string.VoipGroupMutedForMe));
                } else if (a == 3) {
                    statusTextView[a].setTextColor(Theme.getColor(Theme.key_voipgroup_listeningText));
                    statusTextView[a].setText(LocaleController.getString("WantsToSpeak", R.string.WantsToSpeak));
                }
                addView(statusTextView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 54 : 67, 32, LocaleController.isRTL ? 67 : 54, 0));
            }
        }

        fullAboutTextView = new SimpleTextView(context);
        fullAboutTextView.setMaxLines(3);
        fullAboutTextView.setTextSize(15);
        fullAboutTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_mutedIcon));
        fullAboutTextView.setVisibility(View.GONE);
        addView(fullAboutTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20 * 3, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 14, 32, 14, 0));

        muteDrawable = new RLottieDrawable(R.raw.voice_outlined2, "" + R.raw.voice_outlined2, AndroidUtilities.dp(34), AndroidUtilities.dp(32), true, null);
        shakeHandDrawable = new RLottieDrawable(R.raw.hand_1, "" + R.raw.hand_1, AndroidUtilities.dp(34), AndroidUtilities.dp(32), true, null);

        muteButton = new RLottieImageView(context);
        muteButton.setScaleType(ImageView.ScaleType.CENTER);
        muteButton.setAnimation(muteDrawable);
        if (Build.VERSION.SDK_INT >= 21) {
            RippleDrawable rippleDrawable = (RippleDrawable) Theme.createSelectorDrawable(Theme.getColor(grayIconColor) & 0x24ffffff);
            Theme.setRippleDrawableForceSoftware(rippleDrawable);
            muteButton.setBackground(rippleDrawable);
        }
        muteButton.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(muteButton, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 6, 0, 6, 0));
        muteButton.setOnClickListener(v -> onMuteClick(GroupCallUserCell.this));

        avatarWavesDrawable = new AvatarWavesDrawable(AndroidUtilities.dp(26), AndroidUtilities.dp(29));

        setWillNotDraw(false);

        setFocusable(true);
    }

    protected void onMuteClick(GroupCallUserCell cell) {

    }

    public int getClipHeight() {
        SimpleTextView aboutTextView;
        if (!TextUtils.isEmpty(fullAboutTextView.getText()) && hasAvatar) {
            aboutTextView = fullAboutTextView;
        } else {
            aboutTextView = statusTextView[4];
        }
        int lineCount = aboutTextView.getLineCount();
        if (lineCount > 1) {
            int h = aboutTextView.getTextHeight();
            return aboutTextView.getTop() + h + AndroidUtilities.dp(8);
        }
        return getMeasuredHeight();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (updateRunnableScheduled) {
            AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            updateRunnableScheduled = false;
        }
        if (updateVoiceRunnableScheduled) {
            AndroidUtilities.cancelRunOnUIThread(updateVoiceRunnable);
            updateVoiceRunnableScheduled = false;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
    }

    public boolean isSelfUser() {
        if (selfId > 0) {
            return currentUser != null && currentUser.id == selfId;
        } else {
            return currentChat != null && currentChat.id == -selfId;
        }
    }

    public boolean isHandRaised() {
        return lastRaisedHand;
    }

    public CharSequence getName() {
        return nameTextView.getText();
    }

    public boolean hasAvatarSet() {
        return avatarImageView.getImageReceiver().hasNotThumb();
    }

    public void setData(AccountInstance account, TLRPC.TL_groupCallParticipant groupCallParticipant, ChatObject.Call call, long self, TLRPC.FileLocation uploadingAvatar, boolean animated) {
        currentCall = call;
        accountInstance = account;
        selfId = self;

        participant = groupCallParticipant;

        long id = MessageObject.getPeerId(participant.peer);
        if (id > 0) {
            currentUser = accountInstance.getMessagesController().getUser(id);
            currentChat = null;
            avatarDrawable.setInfo(currentUser);

            nameTextView.setText(UserObject.getUserName(currentUser));
            nameTextView.setRightDrawable(currentUser != null && currentUser.verified ? new VerifiedDrawable(getContext()) : null);
            avatarImageView.getImageReceiver().setCurrentAccount(account.getCurrentAccount());
            if (uploadingAvatar != null) {
                hasAvatar = true;
                avatarImageView.setImage(ImageLocation.getForLocal(uploadingAvatar), "50_50", avatarDrawable, null);
            } else {
                ImageLocation imageLocation = ImageLocation.getForUser(currentUser, ImageLocation.TYPE_SMALL);
                hasAvatar = imageLocation != null;
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, currentUser);
            }
        } else {
            currentChat = accountInstance.getMessagesController().getChat(-id);
            currentUser = null;
            avatarDrawable.setInfo(currentChat);

            if (currentChat != null) {
                nameTextView.setText(currentChat.title);
                nameTextView.setRightDrawable(currentChat.verified ? new VerifiedDrawable(getContext()) : null);
                avatarImageView.getImageReceiver().setCurrentAccount(account.getCurrentAccount());
                if (uploadingAvatar != null) {
                    hasAvatar = true;
                    avatarImageView.setImage(ImageLocation.getForLocal(uploadingAvatar), "50_50", avatarDrawable, null);
                } else {
                    ImageLocation imageLocation = ImageLocation.getForChat(currentChat, ImageLocation.TYPE_SMALL);
                    hasAvatar = imageLocation != null;
                    avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, currentChat);
                }
            }
        }
        applyParticipantChanges(animated);
    }

    public void setDrawDivider(boolean draw) {
        needDivider = draw;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyParticipantChanges(false);
    }

    public TLRPC.TL_groupCallParticipant getParticipant() {
        return participant;
    }

    public void setAmplitude(double value) {
        if (value > 1.5f) {
            if (updateRunnableScheduled) {
                AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            }
            if (!isSpeaking) {
                isSpeaking = true;
                applyParticipantChanges(true);
            }
            avatarWavesDrawable.setAmplitude(value);

            AndroidUtilities.runOnUIThread(updateRunnable, 500);
            updateRunnableScheduled = true;
        } else {
            avatarWavesDrawable.setAmplitude(0);
        }
    }

    public boolean clickMuteButton() {
        if (muteButton.isEnabled()) {
            muteButton.callOnClick();
            return true;
        }
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58), MeasureSpec.EXACTLY));
    }

    public void applyParticipantChanges(boolean animated) {
        applyParticipantChanges(animated, false);
    }

    public void setGrayIconColor(String key, int value) {
        if (!grayIconColor.equals(key)) {
            if (currentIconGray) {
                lastMuteColor = Theme.getColor(key);
            }
            grayIconColor = key;
        }
        if (currentIconGray) {
            muteButton.setColorFilter(new PorterDuffColorFilter(value, PorterDuff.Mode.MULTIPLY));
            Theme.setSelectorDrawableColor(muteButton.getDrawable(), value & 0x24ffffff, true);
        }
    }

    public void setAboutVisibleProgress(int color, float progress) {
        if (TextUtils.isEmpty(statusTextView[4].getText())) {
            progress = 0;
        }
        statusTextView[4].setFullAlpha(progress);
        statusTextView[4].setFullLayoutAdditionalWidth(0, 0);
        invalidate();
    }

    public void setAboutVisible(boolean visible) {
        if (visible) {
            statusTextView[4].setTranslationY(0);
        } else {
            statusTextView[4].setFullAlpha(0.0f);
        }
        invalidate();
    }

    private void applyParticipantChanges(boolean animated, boolean internal) {
        if (currentCall == null) {
            return;
        }
        muteButton.setEnabled(!isSelfUser() || participant.raise_hand_rating != 0);

        boolean hasVoice;
        /*if (updateVoiceRunnableScheduled) {
            AndroidUtilities.cancelRunOnUIThread(updateVoiceRunnable);
            updateVoiceRunnableScheduled = false;
        }*/
        if (SystemClock.elapsedRealtime() - participant.lastVoiceUpdateTime < 500) {
            hasVoice = participant.hasVoiceDelayed;
            /*if (hasVoice) {
                AndroidUtilities.runOnUIThread(updateVoiceRunnable, 500 - participant.lastVoiceUpdateTime);
                updateVoiceRunnableScheduled = true;
            }*/
        } else {
            hasVoice = participant.hasVoice;
        }
        if (!internal) {
            long diff = SystemClock.uptimeMillis() - participant.lastSpeakTime;
            boolean newSpeaking = diff < 500;

            if (!isSpeaking || !newSpeaking || hasVoice) {
                isSpeaking = newSpeaking;
                if (updateRunnableScheduled) {
                    AndroidUtilities.cancelRunOnUIThread(updateRunnable);
                    updateRunnableScheduled = false;
                }
                if (isSpeaking) {
                    AndroidUtilities.runOnUIThread(updateRunnable, 500 - diff);
                    updateRunnableScheduled = true;
                }
            }
        }

        TLRPC.TL_groupCallParticipant newParticipant = currentCall.participants.get(MessageObject.getPeerId(participant.peer));
        if (newParticipant != null) {
            participant = newParticipant;
        }

        ArrayList<Animator> animators = null;

        boolean newMuted;
        boolean newRaisedHand = false;
        boolean myted_by_me = participant.muted_by_you && !isSelfUser();

        if (isSelfUser()) {
            newMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute() && (!isSpeaking || !hasVoice);
        } else {
            newMuted = participant.muted && (!isSpeaking || !hasVoice) || myted_by_me;
        }
        boolean newMutedByAdmin = newMuted && !participant.can_self_unmute;
        boolean hasAbout = !TextUtils.isEmpty(participant.about);
        int newMuteColor;
        int newStatus;
        currentIconGray = false;
        AndroidUtilities.cancelRunOnUIThread(checkRaiseRunnable);

        if (participant.muted && !isSpeaking || myted_by_me) {
            if (!participant.can_self_unmute || myted_by_me) {
                if (newRaisedHand = !participant.can_self_unmute && participant.raise_hand_rating != 0) {
                    newMuteColor = Theme.getColor(Theme.key_voipgroup_listeningText);
                    long time = SystemClock.elapsedRealtime() - participant.lastRaiseHandDate;
                    if (participant.lastRaiseHandDate == 0 || time > 5000) {
                        newStatus = myted_by_me ? 2 : (hasAbout ? 4 : 0);
                    } else {
                        newStatus = 3;
                        AndroidUtilities.runOnUIThread(checkRaiseRunnable, 5000 - time);
                    }
                } else {
                    newMuteColor = Theme.getColor(Theme.key_voipgroup_mutedByAdminIcon);
                    newStatus = myted_by_me ? 2 : (hasAbout ? 4 : 0);
                }
            } else {
                newMuteColor = Theme.getColor(grayIconColor);
                currentIconGray = true;
                newStatus = hasAbout ? 4 : 0;
            }
        } else {
            if (isSpeaking && hasVoice) {
                newMuteColor = Theme.getColor(Theme.key_voipgroup_speakingText);
                newStatus = 1;
            } else {
                newMuteColor = Theme.getColor(grayIconColor);
                newStatus = (hasAbout ? 4 : 0);
                currentIconGray = true;
            }
        }

        if (!isSelfUser()) {
            statusTextView[4].setTextColor(Theme.getColor(grayIconColor));
        }

        if (isSelfUser()) {
            if (!hasAbout && !hasAvatar) {
                if (currentUser != null) {
                    statusTextView[4].setText(LocaleController.getString("TapToAddPhotoOrBio", R.string.TapToAddPhotoOrBio));
                } else {
                    statusTextView[4].setText(LocaleController.getString("TapToAddPhotoOrDescription", R.string.TapToAddPhotoOrDescription));
                }
                statusTextView[4].setTextColor(Theme.getColor(grayIconColor));
            } else if (!hasAbout ){
                if (currentUser != null) {
                    statusTextView[4].setText(LocaleController.getString("TapToAddBio", R.string.TapToAddBio));
                } else {
                    statusTextView[4].setText(LocaleController.getString("TapToAddDescription", R.string.TapToAddDescription));
                }
                statusTextView[4].setTextColor(Theme.getColor(grayIconColor));
            } else if (!hasAvatar) {
                statusTextView[4].setText(LocaleController.getString("TapToAddPhoto", R.string.TapToAddPhoto));
                statusTextView[4].setTextColor(Theme.getColor(grayIconColor));
            } else {
                statusTextView[4].setText(LocaleController.getString("ThisIsYou", R.string.ThisIsYou));
                statusTextView[4].setTextColor(Theme.getColor(Theme.key_voipgroup_listeningText));
            }
            if (hasAbout) {
                fullAboutTextView.setText(AndroidUtilities.replaceNewLines(participant.about));
                fullAboutTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_mutedIcon));
            } else {
                fullAboutTextView.setText(statusTextView[newStatus].getText());
                fullAboutTextView.setTextColor(statusTextView[newStatus].getTextColor());
            }
        } else if (hasAbout) {
            statusTextView[4].setText(AndroidUtilities.replaceNewLines(participant.about));
            fullAboutTextView.setText("");
        } else {
            statusTextView[4].setText("");
            fullAboutTextView.setText("");
        }
        boolean somethingChanged = false;
        if (animatorSet != null) {
            if (newStatus != currentStatus || lastMuteColor != newMuteColor) {
                somethingChanged = true;
            }
        }
        if (!animated || somethingChanged) {
            if (animatorSet != null) {
                animatorSet.cancel();
                animatorSet = null;
            }
        }
        if (!animated || lastMuteColor != newMuteColor || somethingChanged) {
            if (animated) {
                animators = new ArrayList<>();
                int oldColor = lastMuteColor;
                lastMuteColor = newMuteColor;
                ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
                animator.addUpdateListener(animation -> {
                    float value = animation.getAnimatedFraction();
                    int color = AndroidUtilities.getOffsetColor(oldColor, newMuteColor, value, 1.0f);
                    muteButton.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    Theme.setSelectorDrawableColor(muteButton.getDrawable(), color & 0x24ffffff, true);
                });
                animators.add(animator);
            } else {
                muteButton.setColorFilter(new PorterDuffColorFilter(lastMuteColor = newMuteColor, PorterDuff.Mode.MULTIPLY));
                Theme.setSelectorDrawableColor(muteButton.getDrawable(), newMuteColor & 0x24ffffff, true);
            }
        }
        if (newStatus == 1) {
            int vol = ChatObject.getParticipantVolume(participant);
            int volume = vol / 100;
            if (volume != 100) {
                statusTextView[1].setLeftDrawable(speakingDrawable);
                statusTextView[1].setText(LocaleController.formatString("SpeakingWithVolume", R.string.SpeakingWithVolume, vol < 100 ? 1 : volume));
            } else {
                statusTextView[1].setLeftDrawable(null);
                statusTextView[1].setText(LocaleController.getString("Speaking", R.string.Speaking));
            }
        }
        if (isSelfUser()) {
            applyStatus(4);
        } else if (!animated || newStatus != currentStatus || somethingChanged) {
            if (animated) {
                if (animators == null) {
                    animators = new ArrayList<>();
                }
                /*for (int a = 0; a < statusTextView.length; a++) {
                    statusTextView[a].setVisibility(VISIBLE);
                }*/
                if (newStatus == 0) {
                    for (int a = 0; a < statusTextView.length; a++) {
                        animators.add(ObjectAnimator.ofFloat(statusTextView[a], View.TRANSLATION_Y, a == newStatus ? 0 : AndroidUtilities.dp(-2)));
                        animators.add(ObjectAnimator.ofFloat(statusTextView[a], View.ALPHA, a == newStatus ? 1.0f : 0.0f));
                    }
                } else {
                    for (int a = 0; a < statusTextView.length; a++) {
                        animators.add(ObjectAnimator.ofFloat(statusTextView[a], View.TRANSLATION_Y, a == newStatus ? 0 : AndroidUtilities.dp(a == 0 ? 2 : -2)));
                        animators.add(ObjectAnimator.ofFloat(statusTextView[a], View.ALPHA, a == newStatus ? 1.0f : 0.0f));
                    }
                }
            } else {
                applyStatus(newStatus);
            }
            currentStatus = newStatus;
        }
        avatarWavesDrawable.setMuted(newStatus, animated);
        if (animators != null) {
            if (animatorSet != null) {
                animatorSet.cancel();
                animatorSet = null;
            }
            animatorSet = new AnimatorSet();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!isSelfUser()) {
                        applyStatus(newStatus);
                    }
                    animatorSet = null;
                }
            });
            animatorSet.playTogether(animators);
            animatorSet.setDuration(180);
            animatorSet.start();
        }

        if (!animated || lastMuted != newMuted || lastRaisedHand != newRaisedHand) {
            boolean changed;
            if (newRaisedHand) {
                changed = muteDrawable.setCustomEndFrame(84);
                if (animated) {
                    muteDrawable.setOnFinishCallback(raiseHandCallback, 83);
                } else {
                    muteDrawable.setOnFinishCallback(null, 0);
                }
            } else {
                muteButton.setAnimation(muteDrawable);
                muteDrawable.setOnFinishCallback(null, 0);
                if (newMuted && lastRaisedHand) {
                    changed = muteDrawable.setCustomEndFrame(21);
                } else {
                    changed = muteDrawable.setCustomEndFrame(newMuted ? 64 : 42);
                }
            }
            if (animated) {
                if (changed) {
                    if (newStatus == 3) {
                        muteDrawable.setCurrentFrame(63);
                    } else {
                        if (newMuted && lastRaisedHand && !newRaisedHand) {
                            muteDrawable.setCurrentFrame(0);
                        } else if (newMuted) {
                            muteDrawable.setCurrentFrame(43);
                        } else {
                            muteDrawable.setCurrentFrame(21);
                        }
                    }
                }
                muteButton.playAnimation();
            } else {
                muteDrawable.setCurrentFrame(muteDrawable.getCustomEndFrame() - 1, false, true);
                muteButton.invalidate();
            }
            lastMuted = newMuted;
            lastRaisedHand = newRaisedHand;
        }
        if (!isSpeaking) {
            avatarWavesDrawable.setAmplitude(0);
        }
        avatarWavesDrawable.setShowWaves(isSpeaking && progressToAvatarPreview == 0, this);
    }

    private void applyStatus(int newStatus) {
        if (newStatus == 0) {
            for (int a = 0; a < statusTextView.length; a++) {
                //statusTextView[a].setVisibility(a == newStatus ? VISIBLE : INVISIBLE);
                statusTextView[a].setTranslationY(a == newStatus ? 0 : AndroidUtilities.dp(-2));
                statusTextView[a].setAlpha(a == newStatus ? 1.0f : 0.0f);
            }
        } else {
            for (int a = 0; a < statusTextView.length; a++) {
                //statusTextView[a].setVisibility(a == newStatus ? VISIBLE : INVISIBLE);
                statusTextView[a].setTranslationY(a == newStatus ? 0 : AndroidUtilities.dp(a == 0 ? 2 : -2));
                statusTextView[a].setAlpha(a == newStatus ? 1.0f : 0.0f);
            }
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (needDivider) {
            if (progressToAvatarPreview != 0) {
                dividerPaint.setAlpha((int) ((1.0f - progressToAvatarPreview) * 255));
            } else {
                dividerPaint.setAlpha((int) ((1.0f - statusTextView[4].getFullAlpha()) * 255));
            }
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(68) : 0), getMeasuredHeight() - 1, dividerPaint);
        }
        int cx = avatarImageView.getLeft() + avatarImageView.getMeasuredWidth() / 2;
        int cy = avatarImageView.getTop() + avatarImageView.getMeasuredHeight() / 2;

        avatarWavesDrawable.update();
        if (progressToAvatarPreview == 0) {
            avatarWavesDrawable.draw(canvas, cx, cy, this);
        }

        avatarImageView.setScaleX(avatarWavesDrawable.getAvatarScale());
        avatarImageView.setScaleY(avatarWavesDrawable.getAvatarScale());

        avatarProgressView.setScaleX(avatarWavesDrawable.getAvatarScale());
        avatarProgressView.setScaleY(avatarWavesDrawable.getAvatarScale());

        super.dispatchDraw(canvas);
    }

    public void getAvatarPosition(int[] pos) {
        avatarImageView.getLocationInWindow(pos);
    }

    public static class AvatarWavesDrawable {

        float amplitude;
        float animateToAmplitude;
        float animateAmplitudeDiff;
        float wavesEnter = 0f;
        boolean showWaves;

        private BlobDrawable blobDrawable;
        private BlobDrawable blobDrawable2;

        private boolean hasCustomColor;
        private int isMuted;
        private float progressToMuted = 0;

        boolean invalidateColor = true;

        public AvatarWavesDrawable(int minRadius, int maxRadius) {
            blobDrawable = new BlobDrawable(6);
            blobDrawable2 = new BlobDrawable(8);
            blobDrawable.minRadius = minRadius;
            blobDrawable.maxRadius = maxRadius;
            blobDrawable2.minRadius = minRadius;
            blobDrawable2.maxRadius = maxRadius;
            blobDrawable.generateBlob();
            blobDrawable2.generateBlob();
            blobDrawable.paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_speakingText), (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
            blobDrawable2.paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_speakingText), (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
        }

        public void update() {
            if (animateToAmplitude != amplitude) {
                amplitude += animateAmplitudeDiff * 16;
                if (animateAmplitudeDiff > 0) {
                    if (amplitude > animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                } else {
                    if (amplitude < animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                }
            }

            if (showWaves && wavesEnter != 1f) {
                wavesEnter += 16 / 350f;
                if (wavesEnter > 1f) {
                    wavesEnter = 1f;
                }
            } else if (!showWaves && wavesEnter != 0) {
                wavesEnter -= 16 / 350f;
                if (wavesEnter < 0f) {
                    wavesEnter = 0f;
                }
            }
        }

        public void draw(Canvas canvas, float cx, float cy, View parentView) {
            float scaleBlob = 0.8f + 0.4f * amplitude;
            if (showWaves || wavesEnter != 0) {
                canvas.save();
                float wavesEnter = CubicBezierInterpolator.DEFAULT.getInterpolation(this.wavesEnter);

                canvas.scale(scaleBlob * wavesEnter, scaleBlob * wavesEnter, cx, cy);

                if (!hasCustomColor) {
                    if (isMuted != 1 && progressToMuted != 1f) {
                        progressToMuted += 16 / 150f;
                        if (progressToMuted > 1f) {
                            progressToMuted = 1f;
                        }
                        invalidateColor = true;
                    } else if (isMuted == 1 && progressToMuted != 0f) {
                        progressToMuted -= 16 / 150f;
                        if (progressToMuted < 0f) {
                            progressToMuted = 0f;
                        }
                        invalidateColor = true;
                    }

                    if (invalidateColor) {
                        int color = ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_speakingText), isMuted == 2 ? Theme.getColor(Theme.key_voipgroup_mutedByAdminIcon) : Theme.getColor(Theme.key_voipgroup_listeningText), progressToMuted);
                        blobDrawable.paint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
                    }
                }

                blobDrawable.update(amplitude, 1f);
                blobDrawable.draw(cx, cy, canvas, blobDrawable.paint);

                blobDrawable2.update(amplitude, 1f);
                blobDrawable2.draw(cx, cy, canvas, blobDrawable.paint);
                canvas.restore();
            }

            if (wavesEnter != 0) {
                parentView.invalidate();
            }
        }

        public float getAvatarScale() {
            float scaleAvatar = 0.9f + 0.2f * amplitude;
            float wavesEnter = CubicBezierInterpolator.EASE_OUT.getInterpolation(this.wavesEnter);
            return scaleAvatar * wavesEnter + 1f * (1f - wavesEnter);
        }

        public void setShowWaves(boolean show, View parentView) {
            if (showWaves != show) {
                parentView.invalidate();
            }
            showWaves = show;
        }

        public void setAmplitude(double value) {
            float amplitude = (float) value / 80f;
            if (!showWaves) {
                amplitude = 0;
            }
            if (amplitude > 1f) {
                amplitude = 1f;
            } else if (amplitude < 0) {
                amplitude = 0;
            }
            animateToAmplitude = amplitude;
            animateAmplitudeDiff = (animateToAmplitude - this.amplitude) / 200;
        }

        public void setColor(int color) {
            hasCustomColor = true;
            blobDrawable.paint.setColor(color);
        }

        public void setMuted(int status, boolean animated) {
            this.isMuted = status;
            if (!animated) {
                progressToMuted = isMuted != 1 ? 1f : 0f;
            }
            invalidateColor = true;
        }
    }

    public BackupImageView getAvatarImageView() {
        return avatarImageView;
    }


    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (info.isEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, participant.muted && !participant.can_self_unmute ? LocaleController.getString("VoipUnmute", R.string.VoipUnmute) : LocaleController.getString("VoipMute", R.string.VoipMute)));
        }
    }

    public long getPeerId() {
        if (participant == null) {
            return 0;
        }
        return MessageObject.getPeerId(participant.peer);
    }
}

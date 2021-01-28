package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
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
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
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
import org.telegram.ui.Components.WaveDrawable;

import java.util.ArrayList;

public class GroupCallUserCell extends FrameLayout {

    private AvatarWavesDrawable avatarWavesDrawable;

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView[] statusTextView = new SimpleTextView[3];
    private RLottieImageView muteButton;
    private RLottieDrawable muteDrawable;

    private AvatarDrawable avatarDrawable;

    private ChatObject.Call currentCall;
    private TLRPC.TL_groupCallParticipant participant;
    private TLRPC.User currentUser;

    private Paint dividerPaint;

    private boolean lastMuted;
    private int lastMuteColor;

    private AccountInstance accountInstance;

    private boolean needDivider;
    private boolean currentIconGray;
    private int currentStatus;

    private String grayIconColor = Theme.key_voipgroup_mutedIcon;

    private Runnable updateRunnable = () -> {
        isSpeaking = false;
        applyParticipantChanges(true, true);
        avatarWavesDrawable.setAmplitude(0);
        updateRunnableScheduled = false;
    };
    private boolean updateRunnableScheduled;
    private boolean isSpeaking;

    private Drawable speakingDrawable;

    private AnimatorSet animatorSet;

    public GroupCallUserCell(Context context) {
        super(context);

        dividerPaint = new Paint();
        dividerPaint.setColor(Theme.getColor(Theme.key_voipgroup_actionBar));

        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 11, 6, LocaleController.isRTL ? 11 : 0, 0));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 54 : 67, 10, LocaleController.isRTL ? 67 : 54, 0));

        speakingDrawable = context.getResources().getDrawable(R.drawable.voice_volume_mini);
        speakingDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_voipgroup_speakingText), PorterDuff.Mode.MULTIPLY));

        for (int a = 0; a < 3; a++) {
            statusTextView[a] = new SimpleTextView(context);
            statusTextView[a].setTextSize(15);
            statusTextView[a].setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            if (a == 0) {
                statusTextView[a].setTextColor(Theme.getColor(Theme.key_voipgroup_listeningText));
                statusTextView[a].setText(LocaleController.getString("Listening", R.string.Listening));
            } else if (a == 1) {
                statusTextView[a].setTextColor(Theme.getColor(Theme.key_voipgroup_speakingText));
                statusTextView[a].setText(LocaleController.getString("Speaking", R.string.Speaking));
                statusTextView[a].setDrawablePadding(AndroidUtilities.dp(2));
            } else {
                statusTextView[a].setTextColor(Theme.getColor(Theme.key_voipgroup_mutedByAdminIcon));
                statusTextView[a].setText(LocaleController.getString("VoipGroupMutedForMe", R.string.VoipGroupMutedForMe));
            }
            addView(statusTextView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 54 : 67, 32, LocaleController.isRTL ? 67 : 54, 0));
        }

        muteDrawable = new RLottieDrawable(R.raw.voice_outlined, "" + R.raw.voice_outlined, AndroidUtilities.dp(19), AndroidUtilities.dp(24), true, null);

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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (updateRunnableScheduled) {
            AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            updateRunnableScheduled = false;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
    }

    public boolean isSelfUser() {
        return UserObject.isUserSelf(currentUser);
    }

    public CharSequence getName() {
        return nameTextView.getText();
    }

    public void setData(AccountInstance account, TLRPC.TL_groupCallParticipant groupCallParticipant, ChatObject.Call call) {
        currentCall = call;
        accountInstance = account;

        participant = groupCallParticipant;

        currentUser = accountInstance.getMessagesController().getUser(participant.user_id);
        avatarDrawable.setInfo(currentUser);

        String lastName = UserObject.getUserName(currentUser);
        nameTextView.setText(lastName);

        avatarImageView.getImageReceiver().setCurrentAccount(account.getCurrentAccount());
        avatarImageView.setImage(ImageLocation.getForUser(currentUser, false), "50_50", avatarDrawable, currentUser);
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

    private void applyParticipantChanges(boolean animated, boolean internal) {
        TLRPC.Chat chat = accountInstance.getMessagesController().getChat(currentCall.chatId);
        muteButton.setEnabled(!isSelfUser());

        if (!internal) {
            long diff = SystemClock.uptimeMillis() - participant.lastSpeakTime;
            boolean newSpeaking = diff < 500;

            if (!isSpeaking || !newSpeaking) {
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

        TLRPC.TL_groupCallParticipant newParticipant = currentCall.participants.get(participant.user_id);
        if (newParticipant != null) {
            participant = newParticipant;
        }

        ArrayList<Animator> animators = null;

        boolean newMuted;
        boolean myted_by_me = participant.muted_by_you && !isSelfUser();
        if (isSelfUser()) {
            newMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute() && (!isSpeaking || !participant.hasVoice);
        } else {
            newMuted = participant.muted && (!isSpeaking || !participant.hasVoice) || myted_by_me;
        }
        boolean newMutedByAdmin = newMuted && !participant.can_self_unmute;
        int newMuteColor;
        int newStatus;
        currentIconGray = false;
        if (participant.muted && !isSpeaking || myted_by_me) {
            if (!participant.can_self_unmute || myted_by_me) {
                newMuteColor = Theme.getColor(Theme.key_voipgroup_mutedByAdminIcon);
            } else {
                newMuteColor = Theme.getColor(grayIconColor);
                currentIconGray = true;
            }
            newStatus = myted_by_me ? 2 : 0;
        } else {
            if (isSpeaking && participant.hasVoice) {
                newMuteColor = Theme.getColor(Theme.key_voipgroup_speakingText);
                newStatus = 1;
            } else {
                newMuteColor = Theme.getColor(grayIconColor);
                newStatus = 0;
                currentIconGray = true;
            }
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
                if (animators == null) {
                    animators = new ArrayList<>();
                }
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
                statusTextView[1].setText((vol < 100 ? 1 : volume) + "% " + LocaleController.getString("Speaking", R.string.Speaking));
            } else {
                statusTextView[1].setLeftDrawable(null);
                statusTextView[1].setText(LocaleController.getString("Speaking", R.string.Speaking));
            }
        }
        if (!animated || newStatus != currentStatus || somethingChanged) {
            if (animated) {
                if (animators == null) {
                    animators = new ArrayList<>();
                }
                statusTextView[0].setVisibility(VISIBLE);
                statusTextView[1].setVisibility(VISIBLE);
                statusTextView[2].setVisibility(VISIBLE);
                if (newStatus == 0) {
                    animators.add(ObjectAnimator.ofFloat(statusTextView[0], View.TRANSLATION_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[0], View.ALPHA, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[1], View.TRANSLATION_Y, -AndroidUtilities.dp(2)));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[1], View.ALPHA, 0.0f));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[2], View.TRANSLATION_Y, -AndroidUtilities.dp(2)));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[2], View.ALPHA, 0.0f));
                } else if (newStatus == 1) {
                    animators.add(ObjectAnimator.ofFloat(statusTextView[0], View.TRANSLATION_Y, AndroidUtilities.dp(2)));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[0], View.ALPHA, 0.0f));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[1], View.TRANSLATION_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[1], View.ALPHA, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[2], View.TRANSLATION_Y, -AndroidUtilities.dp(2)));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[2], View.ALPHA, 0.0f));
                } else {
                    animators.add(ObjectAnimator.ofFloat(statusTextView[0], View.TRANSLATION_Y, AndroidUtilities.dp(2)));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[0], View.ALPHA, 0.0f));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[1], View.TRANSLATION_Y, -AndroidUtilities.dp(2)));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[1], View.ALPHA, 0.0f));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[2], View.TRANSLATION_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(statusTextView[2], View.ALPHA, 1.0f));
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
                    applyStatus(newStatus);
                    animatorSet = null;
                }
            });
            animatorSet.playTogether(animators);
            animatorSet.setDuration(180);
            animatorSet.start();
        }

        if (!animated || lastMuted != newMuted) {
            boolean changed = muteDrawable.setCustomEndFrame(newMuted ? 13 : 24);
            if (animated) {
                if (changed) {
                    if (newMuted) {
                        muteDrawable.setCurrentFrame(0);
                    } else {
                        muteDrawable.setCurrentFrame(12);
                    }
                }
                muteButton.playAnimation();
            } else {
                muteDrawable.setCurrentFrame(muteDrawable.getCustomEndFrame() - 1, false, true);
                muteButton.invalidate();
            }
            lastMuted = newMuted;
        }
        if (!isSpeaking) {
            avatarWavesDrawable.setAmplitude(0);
        }
        avatarWavesDrawable.setShowWaves(isSpeaking, this);
    }

    private void applyStatus(int newStatus) {
        if (newStatus == 0) {
            statusTextView[0].setVisibility(VISIBLE);
            statusTextView[0].setTranslationY(0);
            statusTextView[0].setAlpha(1.0f);
            statusTextView[1].setVisibility(INVISIBLE);
            statusTextView[1].setTranslationY(-AndroidUtilities.dp(2));
            statusTextView[1].setAlpha(0.0f);
            statusTextView[2].setVisibility(INVISIBLE);
            statusTextView[2].setTranslationY(-AndroidUtilities.dp(2));
            statusTextView[2].setAlpha(0.0f);
        } else if (newStatus == 1) {
            statusTextView[0].setVisibility(INVISIBLE);
            statusTextView[0].setTranslationY(AndroidUtilities.dp(2));
            statusTextView[0].setAlpha(0.0f);
            statusTextView[1].setVisibility(VISIBLE);
            statusTextView[1].setTranslationY(0);
            statusTextView[1].setAlpha(1.0f);
            statusTextView[2].setVisibility(INVISIBLE);
            statusTextView[2].setTranslationY(-AndroidUtilities.dp(2));
            statusTextView[2].setAlpha(0.0f);
        } else {
            statusTextView[0].setVisibility(INVISIBLE);
            statusTextView[0].setTranslationY(AndroidUtilities.dp(2));
            statusTextView[0].setAlpha(0.0f);
            statusTextView[1].setVisibility(INVISIBLE);
            statusTextView[1].setTranslationY(-AndroidUtilities.dp(2));
            statusTextView[1].setAlpha(0.0f);
            statusTextView[2].setVisibility(VISIBLE);
            statusTextView[2].setTranslationY(0);
            statusTextView[2].setAlpha(1.0f);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(68) : 0), getMeasuredHeight() - 1, dividerPaint);
        }
        int cx = avatarImageView.getLeft() + avatarImageView.getMeasuredWidth() / 2;
        int cy = avatarImageView.getTop() + avatarImageView.getMeasuredHeight() / 2;

        avatarWavesDrawable.update();
        avatarWavesDrawable.draw(canvas, cx, cy, this);

        avatarImageView.setScaleX(avatarWavesDrawable.getAvatarScale());
        avatarImageView.setScaleY(avatarWavesDrawable.getAvatarScale());
        super.dispatchDraw(canvas);
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

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (info.isEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, participant.muted && !participant.can_self_unmute ? LocaleController.getString("VoipUnmute", R.string.VoipUnmute) : LocaleController.getString("VoipMute", R.string.VoipMute)));
        }
    }
}

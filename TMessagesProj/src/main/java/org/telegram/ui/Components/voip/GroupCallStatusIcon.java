package org.telegram.ui.Components.voip;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

public class GroupCallStatusIcon {

    RLottieDrawable micDrawable;
    RLottieDrawable shakeHandDrawable;
    RLottieImageView iconView;

    boolean updateRunnableScheduled;
    boolean isSpeaking;

    boolean lastMuted;
    boolean lastRaisedHand;
    Callback callback;
    TLRPC.TL_groupCallParticipant participant;

    private Runnable shakeHandCallback = () -> {
        shakeHandDrawable.setOnFinishCallback(null, 0);
        micDrawable.setOnFinishCallback(null, 0);
        if (iconView != null) {
            iconView.setAnimation(micDrawable);
        }
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
        shakeHandDrawable.setCurrentFrame(startFrame);

        if (iconView != null) {
            iconView.setAnimation(shakeHandDrawable);
            iconView.playAnimation();
        }
    };

    private boolean mutedByMe;

    public GroupCallStatusIcon() {
        micDrawable = new RLottieDrawable(R.raw.voice_mini, "" + R.raw.voice_mini, AndroidUtilities.dp(24), AndroidUtilities.dp(24), true, null);
        shakeHandDrawable = new RLottieDrawable(R.raw.hand_2, "" + R.raw.hand_2, AndroidUtilities.dp(15), AndroidUtilities.dp(15), true, null);
    }

    private Runnable updateRunnable = () -> {
        isSpeaking = false;
        if (callback != null) {
            callback.onStatusChanged();
        }
        updateRunnableScheduled = false;
    };

    public void setAmplitude(double value) {
        if (value > 1.5f) {
            if (updateRunnableScheduled) {
                AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            }
            if (!isSpeaking) {
                isSpeaking = true;
                if (callback != null) {
                    callback.onStatusChanged();
                }
            }

            AndroidUtilities.runOnUIThread(updateRunnable, 500);
            updateRunnableScheduled = true;
        }
    }

    private Runnable checkRaiseRunnable = () -> {
        updateIcon(true);
    };

    public void setImageView(RLottieImageView iconView) {
        this.iconView = iconView;
        updateIcon(false);
    }

    public void setParticipant(TLRPC.TL_groupCallParticipant participant, boolean animated) {
        this.participant = participant;
        updateIcon(animated);
    }

    public void updateIcon(boolean animated) {
        if (iconView == null || participant == null || micDrawable == null) {
            return;
        }
        boolean changed;
        boolean newMutedByMe = participant.muted_by_you && !participant.self;
        boolean newMuted;
        boolean hasVoice;
        if (SystemClock.elapsedRealtime() - participant.lastVoiceUpdateTime < 500) {
            hasVoice = participant.hasVoiceDelayed;
        } else {
            hasVoice = participant.hasVoice;
        }
        if (participant.self) {
            newMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute() && (!isSpeaking || !hasVoice);
        } else {
            newMuted = participant.muted && (!isSpeaking || !hasVoice) || newMutedByMe;
        }
        boolean newRaisedHand = (participant.muted && !isSpeaking || newMutedByMe) && (!participant.can_self_unmute || newMutedByMe) && (!participant.can_self_unmute && participant.raise_hand_rating != 0);
        int newStatus = 0;
        if (newRaisedHand) {
            long time = SystemClock.elapsedRealtime() - participant.lastRaiseHandDate;
            if (participant.lastRaiseHandDate == 0 || time > 5000) {
                newStatus = newMutedByMe ? 2 : 0;
            } else {
                newStatus = 3;
                AndroidUtilities.runOnUIThread(checkRaiseRunnable, 5000 - time);
            }

            changed = micDrawable.setCustomEndFrame(136);
//            if (animated) {
//                micDrawable.setOnFinishCallback(raiseHandCallback, 135);
//            } else {
//                micDrawable.setOnFinishCallback(null, 0);
//            }
        } else {
            iconView.setAnimation(micDrawable);
            micDrawable.setOnFinishCallback(null, 0);
            if (newMuted && lastRaisedHand) {
                changed = micDrawable.setCustomEndFrame(36);
            } else {
                changed = micDrawable.setCustomEndFrame(newMuted ? 99 : 69);
            }
        }

        if (animated) {
            if (changed) {
                if (newRaisedHand) {
                    micDrawable.setCurrentFrame(99);
                    micDrawable.setCustomEndFrame(136);
                } else if (newMuted && lastRaisedHand && !newRaisedHand) {
                    micDrawable.setCurrentFrame(0);
                    micDrawable.setCustomEndFrame(36);
                } else if (newMuted) {
                    micDrawable.setCurrentFrame(69);
                    micDrawable.setCustomEndFrame(99);
                } else {
                    micDrawable.setCurrentFrame(36);
                    micDrawable.setCustomEndFrame(69);
                }
                iconView.playAnimation();
                iconView.invalidate();
            }
        } else {
            micDrawable.setCurrentFrame(micDrawable.getCustomEndFrame() - 1, false, true);
            iconView.invalidate();
        }

        iconView.setAnimation(micDrawable);
        lastMuted = newMuted;
        lastRaisedHand = newRaisedHand;

        if (mutedByMe != newMutedByMe) {
            mutedByMe = newMutedByMe;
            if (callback != null) {
                callback.onStatusChanged();
            }
        }
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    public boolean isMutedByMe() {
        return mutedByMe;
    }

    public boolean isMutedByAdmin() {
        return participant != null && participant.muted && !participant.can_self_unmute;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
        if (callback == null) {
            isSpeaking = false;
            AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            AndroidUtilities.cancelRunOnUIThread(raiseHandCallback);
            AndroidUtilities.cancelRunOnUIThread(checkRaiseRunnable);
            micDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        }
    }

    public interface Callback {
        void onStatusChanged();
    }
}

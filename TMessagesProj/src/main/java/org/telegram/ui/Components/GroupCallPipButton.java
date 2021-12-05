package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.Random;

public class GroupCallPipButton extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, VoIPService.StateListener {

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    BlobDrawable blobDrawable = new BlobDrawable(8);
    BlobDrawable blobDrawable2 = new BlobDrawable(9);

    float amplitude;
    float animateToAmplitude;
    float animateAmplitudeDiff;

    WeavingState currentState;
    WeavingState previousState;

    float progressToState = 1f;

    boolean prepareToRemove;
    float progressToPrepareRemove;
    private final LinearGradient prepareToRemoveShader;
    Matrix matrix = new Matrix();

    float wavesEnter = 0f;
    private final int currentAccount;

    public final static int MUTE_BUTTON_STATE_MUTE = 1;
    public final static int MUTE_BUTTON_STATE_UNMUTE = 0;
    public final static int MUTE_BUTTON_STATE_RECONNECT = 2;
    public final static int MUTE_BUTTON_STATE_MUTED_BY_ADMIN = 3;

    private RLottieImageView muteButton;
    private RLottieDrawable bigMicDrawable;
    long lastStubUpdateAmplitude;

    private boolean stub;
    Random random = new Random();
    public boolean removed;

    public GroupCallPipButton(Context context, int currentAccount, boolean stub) {
        super(context);
        this.stub = stub;
        this.currentAccount = currentAccount;

        for (int i = 0; i < 4; i++) {
            states[i] = new WeavingState(i);
        }

        blobDrawable.maxRadius = AndroidUtilities.dp(37);
        blobDrawable.minRadius = AndroidUtilities.dp(32);
        blobDrawable2.maxRadius = AndroidUtilities.dp(37);
        blobDrawable2.minRadius = AndroidUtilities.dp(32);
        blobDrawable.generateBlob();
        blobDrawable2.generateBlob();

        bigMicDrawable = new RLottieDrawable(R.raw.voice_outlined, "" + R.raw.voice_outlined, AndroidUtilities.dp(22), AndroidUtilities.dp(30), true, null);
        setWillNotDraw(false);

        muteButton = new RLottieImageView(context);
        muteButton.setAnimation(bigMicDrawable);
        muteButton.setScaleType(ImageView.ScaleType.CENTER);
        addView(muteButton);

        prepareToRemoveShader = new LinearGradient(0, 0, AndroidUtilities.dp(100 + 250), 0, new int[] {0xFFD54141, 0xFFF76E7E, Color.TRANSPARENT}, new float[] {0, 0.4f, 1f}, Shader.TileMode.CLAMP);

        if (stub) {
            setState(MUTE_BUTTON_STATE_UNMUTE);
        }
    }

    WeavingState[] states = new WeavingState[4];
    boolean pressedState;
    float pressedProgress;
    float pinnedProgress;

    public void setPressedState(boolean pressedState) {
        this.pressedState = pressedState;
    }

    public void setPinnedProgress(float pinnedProgress) {
        this.pinnedProgress = pinnedProgress;
    }


    public static class WeavingState {

        private float targetX = -1f;
        private float targetY = -1f;
        private float startX;
        private float startY;
        private float duration;
        private float time;

        public Shader shader;
        private final Matrix matrix = new Matrix();
        private final int currentState;
        int color1;
        int color2;
        int color3;

        public WeavingState(int state) {
            currentState = state;
        }

        public void update(long dt, float amplitude) {
            if (currentState == MUTE_BUTTON_STATE_UNMUTE) {
                if (color1 != Theme.getColor(Theme.key_voipgroup_overlayGreen1) || color2 != Theme.getColor(Theme.key_voipgroup_overlayGreen2)) {
                    shader = new RadialGradient(200, 200, 200, new int[]{color1 = Theme.getColor(Theme.key_voipgroup_overlayGreen1), color2 = Theme.getColor(Theme.key_voipgroup_overlayGreen2)}, null, Shader.TileMode.CLAMP);
                }
            } else if (currentState == MUTE_BUTTON_STATE_MUTE) {
                if (color1 != Theme.getColor(Theme.key_voipgroup_overlayBlue1) || color2 != Theme.getColor(Theme.key_voipgroup_overlayBlue2)) {
                    shader = new RadialGradient(200, 200, 200, new int[]{color1 = Theme.getColor(Theme.key_voipgroup_overlayBlue1), color2 = Theme.getColor(Theme.key_voipgroup_overlayBlue2)}, null, Shader.TileMode.CLAMP);
                }
            } else if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN){
                if (color1 != Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient) || color2 != Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient2) || color3 != Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3)) {
                    shader = new RadialGradient(200, 200, 200, new int[]{color2 = Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient2), color3 = Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3), color1 = Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient)}, null, Shader.TileMode.CLAMP);
                }
            } else {
                return;
            }
            int width = AndroidUtilities.dp(130);
            int height = width;
            if (duration == 0 || time >= duration) {
                duration = Utilities.random.nextInt(700) + 500;
                time = 0;
                if (targetX == -1f) {
                    updateTargets();
                }
                startX = targetX;
                startY = targetY;
                updateTargets();
            }
            time += dt * (0.5f + BlobDrawable.GRADIENT_SPEED_MIN) + dt * (BlobDrawable.GRADIENT_SPEED_MAX * 2) * amplitude;
            if (time > duration) {
                time = duration;
            }
            float interpolation = CubicBezierInterpolator.EASE_OUT.getInterpolation(time / duration);
            float x = width * (startX + (targetX - startX) * interpolation) - 200;
            float y = height * (startY + (targetY - startY) * interpolation) - 200;

            float s;
            if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                s = 2f;
            } else if (currentState == MUTE_BUTTON_STATE_UNMUTE) {
                s = 1.5f;
            } else {
                s = 1.5f;
            }
            float scale = width / 400.0f * s;
            matrix.reset();
            matrix.postTranslate(x, y);
            matrix.postScale(scale, scale, x + 200, y + 200);

            shader.setLocalMatrix(matrix);
        }

        private void updateTargets() {
            if (currentState == MUTE_BUTTON_STATE_UNMUTE) {
                targetX = 0.2f + 0.1f * Utilities.random.nextInt(100) / 100f;
                targetY = 0.7f + 0.1f * Utilities.random.nextInt(100) / 100f;
            } else if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                targetX = 0.6f + 0.1f * Utilities.random.nextInt(100) / 100f;
                targetY = 0.1f * Utilities.random.nextInt(100) / 100f;
            } else {
                targetX = 0.8f + 0.2f * (Utilities.random.nextInt(100) / 100f);
                targetY = Utilities.random.nextInt(100) / 100f;
            }
        }

        public void setToPaint(Paint paint) {
            if (currentState == MUTE_BUTTON_STATE_RECONNECT) {
                paint.setShader(null);
                paint.setColor(Theme.getColor(Theme.key_voipgroup_topPanelGray));
            } else {
                paint.setShader(shader);
            }
        }
    }

    OvershootInterpolator overshootInterpolator = new OvershootInterpolator();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getAlpha() == 0) {
            return;
        }
        float cx = getMeasuredWidth() >> 1;
        float cy = getMeasuredHeight() >> 1;

        if (pressedState && pressedProgress != 1f) {
            pressedProgress += 16f / 150f;
            if (pressedProgress > 1f) {
                pressedProgress = 1f;
            }
        } else if (!pressedState && pressedProgress != 0) {
            pressedProgress -= 16f / 150f;
            if (pressedProgress < 0f) {
                pressedProgress = 0f;
            }
        }

        float pressedProgress = CubicBezierInterpolator.DEFAULT.getInterpolation(this.pressedProgress);
        muteButton.setScaleY(1f + 0.1f * pressedProgress);
        muteButton.setScaleX(1f + 0.1f * pressedProgress);

        if (stub) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastStubUpdateAmplitude > 1000) {
                lastStubUpdateAmplitude = currentTime;
                animateToAmplitude = 0.5f + 0.5f * Math.abs(random.nextInt() % 100) / 100f;
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100 + 1500.0f * BlobDrawable.AMPLITUDE_SPEED);
            }
        }

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

        if (previousState != null) {
            progressToState += 16 / 250f;
            if (progressToState > 1f) {
                progressToState = 1f;
                previousState = null;
            }
        }

        if (prepareToRemove && progressToPrepareRemove != 1f) {
            progressToPrepareRemove += 16f / 350f;
            if (progressToPrepareRemove > 1f) {
                progressToPrepareRemove = 1f;
            }
            if (removed) {
                invalidate();
            }
        } else if (!prepareToRemove && progressToPrepareRemove != 0) {
            progressToPrepareRemove -= 16f / 350f;
            if (progressToPrepareRemove < 0f) {
                progressToPrepareRemove = 0f;
            }
        }

        boolean showWaves = true;
        if (currentState.currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN || currentState.currentState == MUTE_BUTTON_STATE_RECONNECT) {
            showWaves = false;
        }

        if (showWaves && wavesEnter != 1f) {
            wavesEnter += 16f / 350f;
            if (wavesEnter > 1f) {
                wavesEnter = 1f;
            }
        } else if (!showWaves && wavesEnter != 0f) {
            wavesEnter -= 16f / 350f;
            if (wavesEnter < 0f) {
                wavesEnter = 0f;
            }
        }

        float wavesEnter = 0.65f + 0.35f * overshootInterpolator.getInterpolation(this.wavesEnter);

        blobDrawable.update(amplitude, stub ? 0.1f : 0.8f);
        blobDrawable2.update(amplitude, stub ? 0.1f : 0.8f);


        for (int i = 0; i < 3; i++) {
            float alpha;
            if (i == 0 && previousState == null) {
                continue;
            }

            if (i == 0) {
                if (progressToPrepareRemove == 1f) {
                    continue;
                }
                alpha = 1f - progressToState;
                previousState.update(16, amplitude);
                previousState.setToPaint(paint);
            } else if (i == 1) {
                if (currentState == null) {
                    return;
                }
                if (progressToPrepareRemove == 1f) {
                    continue;
                }
                alpha = previousState != null ? progressToState : 1f;
                currentState.update(16, amplitude);
                currentState.setToPaint(paint);
            } else {
                if (progressToPrepareRemove == 0) {
                    continue;
                }
                alpha = 1f;
                paint.setColor(Color.RED);
                matrix.reset();
                matrix.postTranslate(-AndroidUtilities.dp(250) * (1f - progressToPrepareRemove), 0);
                matrix.postRotate(removeAngle, cx, cy);
                prepareToRemoveShader.setLocalMatrix(matrix);
                paint.setShader(prepareToRemoveShader);
            }

            blobDrawable.maxRadius = AndroidUtilities.dp(40);
            blobDrawable.minRadius = AndroidUtilities.dp(32);

            blobDrawable2.maxRadius = AndroidUtilities.dp(38);
            blobDrawable2.minRadius = AndroidUtilities.dp(33);


            if (i != 2) {
                paint.setAlpha((int) (76 * alpha * (1f - progressToPrepareRemove)));
            } else {
                paint.setAlpha((int) (76 * alpha * progressToPrepareRemove));
            }

            if (this.wavesEnter != 0) {
                float scale = (1f + 0.3f * amplitude + 0.1f * pressedProgress) * (1f - pinnedProgress);
                scale = Math.min(scale, 1.3f) * wavesEnter;
                canvas.save();
                canvas.scale(scale, scale, cx, cy);
                blobDrawable.draw(cx, cy, canvas, paint);
                canvas.restore();

                scale = (1f + 0.26f * amplitude + 0.1f * pressedProgress) * (1f - pinnedProgress);
                scale = Math.min(scale, 1.3f) * wavesEnter;
                canvas.save();
                canvas.scale(scale, scale, cx, cy);
                blobDrawable2.draw(cx, cy, canvas, paint);
                canvas.restore();
            }

            if (i == 2) {
                paint.setAlpha((int) (255 * progressToPrepareRemove));
            } else if (i == 1) {
                paint.setAlpha((int) (255 * alpha));
            } else {
                paint.setAlpha(255);
            }
            canvas.save();
            canvas.scale(1f + 0.1f * pressedProgress,1f + 0.1f * pressedProgress, cx, cy);
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(32), paint);
            canvas.restore();
        }

        if (!removed && this.wavesEnter > 0) {
            invalidate();
        }
    }


    public final static float MAX_AMPLITUDE = 8_500f;

    private void setAmplitude(double value) {
        animateToAmplitude = (float) (Math.min(MAX_AMPLITUDE, value) / MAX_AMPLITUDE);
        animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100 + 500.0f * BlobDrawable.AMPLITUDE_SPEED);
    }

    public void setState(int state) {
        if (currentState != null && currentState.currentState == state) {
            return;
        }
        previousState = currentState;
        currentState = states[state];
        if (previousState != null) {
            progressToState = 0;
        } else {
            progressToState = 1;
            boolean showWaves = true;
            if (currentState.currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN || currentState.currentState == MUTE_BUTTON_STATE_RECONNECT) {
                showWaves = false;
            }
            wavesEnter = showWaves ? 1f : 0f;
        }
        String contentDescription;
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService != null && ChatObject.isChannelOrGiga(voIPService.getChat())) {
            contentDescription = LocaleController.getString("VoipChannelVoiceChat", R.string.VoipChannelVoiceChat);
        } else {
            contentDescription = LocaleController.getString("VoipGroupVoiceChat", R.string.VoipGroupVoiceChat);
        }
        if (state == MUTE_BUTTON_STATE_UNMUTE) {
            contentDescription +=  ", " + LocaleController.getString("VoipTapToMute", R.string.VoipTapToMute);
        } else if (state == MUTE_BUTTON_STATE_RECONNECT) {
            contentDescription += ", " + LocaleController.getString("Connecting", R.string.Connecting);
        } else if (state == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
            contentDescription += ", " + LocaleController.getString("VoipMutedByAdmin", R.string.VoipMutedByAdmin);
        }
        setContentDescription(contentDescription);
        invalidate();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && GroupCallPip.getInstance() != null) {
            final String label = GroupCallPip.getInstance().showAlert ? LocaleController.getString("AccDescrCloseMenu", R.string.AccDescrCloseMenu) : LocaleController.getString("AccDescrOpenMenu2", R.string.AccDescrOpenMenu2);
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, label));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!stub) {
            setAmplitude(0);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupCallUpdated);

            boolean isMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute();
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().registerStateListener(this);
            }
            bigMicDrawable.setCustomEndFrame(isMuted ? 13 : 24);
            bigMicDrawable.setCurrentFrame(bigMicDrawable.getCustomEndFrame() - 1, false, true);
            updateButtonState();
        }
    }

    private void updateButtonState() {
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService != null && voIPService.groupCall != null) {
            int currentCallState = voIPService.getCallState();
            if (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING) {
                setState(FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_CONNECTING);
            } else {
                TLRPC.TL_groupCallParticipant participant = voIPService.groupCall.participants.get(voIPService.getSelfId());
                if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(voIPService.getChat())) {
                    if (!voIPService.isMicMute()) {
                        voIPService.setMicMute(true, false, false);
                    }
                    setState(FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_MUTED_BY_ADMIN);
                    final long now = SystemClock.uptimeMillis();
                    final MotionEvent e = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    if (getParent() != null) {
                        View parentView = (View) getParent();
                        parentView.dispatchTouchEvent(e);
                    }
                } else {
                    boolean isMuted = voIPService.isMicMute();
                    setState(isMuted ? FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_MUTE : FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_UNMUTE);
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!stub) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupCallUpdated);
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().unregisterStateListener(this);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.webRtcMicAmplitudeEvent) {
            float amplitude = (float) args[0];
            setAmplitude(amplitude * 4000.0f);
        } else if (id == NotificationCenter.groupCallUpdated) {
            updateButtonState();
        }
    }

    @Override
    public void onAudioSettingsChanged() {
        boolean isMuted = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute();
        boolean changed = bigMicDrawable.setCustomEndFrame(isMuted ? 13 : 24);
        if (changed) {
            if (isMuted) {
                bigMicDrawable.setCurrentFrame(0);
            } else {
                bigMicDrawable.setCurrentFrame(12);
            }
        }
        muteButton.playAnimation();
        updateButtonState();
    }

    @Override
    public void onStateChanged(int state) {
        updateButtonState();
    }

    float removeAngle;

    public void setRemoveAngle(double angle) {
        removeAngle = (float) angle;
    }
    public void prepareToRemove(boolean prepare) {
        if (this.prepareToRemove != prepare) {
            invalidate();
        }
        this.prepareToRemove = prepare;

    }
}

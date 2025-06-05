package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class FragmentContextViewWavesDrawable {

    public final static int MUTE_BUTTON_STATE_MUTE = 1;
    public final static int MUTE_BUTTON_STATE_UNMUTE = 0;
    public final static int MUTE_BUTTON_STATE_CONNECTING = 2;
    public final static int MUTE_BUTTON_STATE_MUTED_BY_ADMIN = 3;

    WeavingState[] states = new WeavingState[4];
    WeavingState currentState;
    WeavingState previousState;
    WeavingState pausedState;

    private float amplitude;
    private float amplitude2;
    private float animateToAmplitude;
    private float animateAmplitudeDiff;
    private float animateAmplitudeDiff2;
    private long lastUpdateTime;
    float progressToState = 1f;

    ArrayList<View> parents = new ArrayList<>();

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    LineBlobDrawable lineBlobDrawable = new LineBlobDrawable(5);
    LineBlobDrawable lineBlobDrawable1 = new LineBlobDrawable(7);
    LineBlobDrawable lineBlobDrawable2 = new LineBlobDrawable(8);

    RectF rect = new RectF();
    Path path = new Path();
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FragmentContextViewWavesDrawable() {
        for (int i = 0; i < 4; i++) {
            states[i] = new WeavingState(i);
        }
    }

    public void draw(float left, float top, float right, float bottom, Canvas canvas, FragmentContextView parentView, float progress) {
        boolean update;
        checkColors();
        if (parentView == null) {
            update = false;
        } else {
            update = parents.size() > 0;
        }
        if (top > bottom) {
            return;
        }
        long dt = 0;
        boolean rippleTransition = currentState != null && previousState != null && ((currentState.currentState == MUTE_BUTTON_STATE_MUTE && previousState.currentState == MUTE_BUTTON_STATE_UNMUTE) || (previousState.currentState == MUTE_BUTTON_STATE_MUTE && currentState.currentState == MUTE_BUTTON_STATE_UNMUTE));

        if (update) {
            long newTime = SystemClock.elapsedRealtime();
            dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            if (dt > 20) {
                dt = 17;
            }
            if (dt < 3) {
                update = false;
            }
        }
        if (update) {
            if (animateToAmplitude != amplitude) {
                amplitude += animateAmplitudeDiff * dt;
                if (animateAmplitudeDiff > 0) {
                    if (amplitude > animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                } else {
                    if (amplitude < animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                }
                parentView.invalidate();
            }

            if (animateToAmplitude != amplitude2) {
                amplitude2 += animateAmplitudeDiff2 * dt;
                if (animateAmplitudeDiff2 > 0) {
                    if (amplitude2 > animateToAmplitude) {
                        amplitude2 = animateToAmplitude;
                    }
                } else {
                    if (amplitude2 < animateToAmplitude) {
                        amplitude2 = animateToAmplitude;
                    }
                }
                parentView.invalidate();
            }

            if (previousState != null) {
                progressToState += dt / 250f;
                if (progressToState > 1f) {
                    progressToState = 1f;
                    previousState = null;
                }
                parentView.invalidate();
            }
        }
        for (int i = 0; i < 2; i++) {
            float alpha;
            if (i == 0 && previousState == null) {
                continue;
            }

            if (i == 0) {
                alpha = 1f - progressToState;
                previousState.setToPaint(paint);
            } else {
                if (currentState == null) {
                    return;
                }
                alpha = previousState != null ? progressToState : 1f;
                if (update) {
                    currentState.update((int) (bottom - top), (int) (right - left), dt, amplitude);
                }
                currentState.setToPaint(paint);
            }

            lineBlobDrawable.minRadius = 0;
            lineBlobDrawable.maxRadius = AndroidUtilities.dp(2) + AndroidUtilities.dp(2) * amplitude;

            lineBlobDrawable1.minRadius = AndroidUtilities.dp(0);
            lineBlobDrawable1.maxRadius = AndroidUtilities.dp(3) + AndroidUtilities.dp(9) * amplitude;

            lineBlobDrawable2.minRadius = AndroidUtilities.dp(0);
            lineBlobDrawable2.maxRadius = AndroidUtilities.dp(3) + AndroidUtilities.dp(9) * amplitude;

            if (i == 1 && update) {
                lineBlobDrawable.update(amplitude, 0.3f);
                lineBlobDrawable1.update(amplitude, 0.7f);
                lineBlobDrawable2.update(amplitude, 0.7f);
            }

            if (LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
                paint.setAlpha((int) (76 * alpha));
                float top1 = AndroidUtilities.dp(6) * amplitude2;
                float top2 = AndroidUtilities.dp(6) * amplitude2;
                lineBlobDrawable1.draw(left, top - top1, right, bottom, canvas, paint, top, progress);
                lineBlobDrawable2.draw(left, top - top2, right, bottom, canvas, paint, top, progress);
            }

            if (i == 1 && rippleTransition) {
                paint.setAlpha(255);
            } else if (i == 1) {
                paint.setAlpha((int) (255 * alpha));
            } else {
                paint.setAlpha(255);
            }
            if (i == 1 && rippleTransition) {
                path.reset();
                float cx = right - AndroidUtilities.dp(18);
                float cy = top + (bottom - top) / 2;
                float r = (right - left) * 1.1f * alpha;
                path.addCircle(cx, cy, r, Path.Direction.CW);
                canvas.save();

                canvas.clipPath(path);
                lineBlobDrawable.draw(left, top, right, bottom, canvas, paint, top, progress);
                canvas.restore();
            } else {
                lineBlobDrawable.draw(left, top, right, bottom, canvas, paint, top, progress);
            }
        }
    }

    float pressedProgress;
    float pressedRemoveProgress;

    private void checkColors() {
        for (int i = 0; i < states.length; i++) {
            states[i].checkColor();
        }
    }

    private void setState(int state, boolean animated) {
        if (currentState != null && currentState.currentState == state) {
            return;
        }
        if (VoIPService.getSharedInstance() == null && currentState == null) {
            currentState = pausedState;
        } else {
            previousState = animated ? currentState : null;
            currentState = states[state];
            if (previousState != null) {
                progressToState = 0;
            } else {
                progressToState = 1;
            }
        }
    }

    public void setAmplitude(float value) {
        animateToAmplitude = value;
        animateAmplitudeDiff = (animateToAmplitude - amplitude) / (250);
        animateAmplitudeDiff2 = (animateToAmplitude - amplitude) / (120);
    }

    public void addParent(View parent) {
        if (!parents.contains(parent)) {
            parents.add(parent);
        }
    }
    public void removeParent(View parent) {
        parents.remove(parent);
        if (parents.isEmpty()) {
            pausedState = currentState;
            currentState = null;
            previousState = null;
        }
    }

    public void updateState(boolean animated) {
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService != null) {
            int currentCallState = voIPService.getCallState();
            if (!voIPService.isSwitchingStream() && (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING)) {
                setState(FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_CONNECTING, animated);
            } else {
                if (voIPService.groupCall != null) {
                    TLRPC.GroupCallParticipant participant = voIPService.groupCall.participants.get(voIPService.getSelfId());
                    if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(voIPService.getChat()) || voIPService.groupCall.call.rtmp_stream) {
                        voIPService.setMicMute(true, false, false);
                        setState(FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_MUTED_BY_ADMIN, animated);
                    } else {
                        boolean isMuted = voIPService.isMicMute();
                        setState(isMuted ? FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_MUTE : FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_UNMUTE, animated);
                    }
                } else {
                    boolean isMuted = voIPService.isMicMute();
                    setState(isMuted ? FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_MUTE : FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_UNMUTE, animated);
                }
            }
        }
    }

    public int getState() {
        return currentState != null ? currentState.currentState : FragmentContextViewWavesDrawable.MUTE_BUTTON_STATE_UNMUTE;
    }

    public long getRippleFinishedDelay() {
        if (pressedProgress != 0 && pressedProgress != 1) {
            return (long) ((1f - pressedProgress) * 150);
        }
        return 0;
    }

    public static class WeavingState {

        private float targetX = -1f;
        private float targetY = -1f;
        private float startX;
        private float startY;
        private float duration;
        private float time;

        public Shader shader;
        public int averageColor;
        private final Matrix matrix = new Matrix();
        private final int currentState;

        int color1;
        int color2;
        int color3;
        public WeavingState(int state) {
            currentState = state;
            createGradients();
        }

        int greenKey1 = Theme.key_voipgroup_topPanelGreen1;
        int greenKey2 = Theme.key_voipgroup_topPanelGreen2;
        int blueKey1 = Theme.key_voipgroup_topPanelBlue1;
        int blueKey2 = Theme.key_voipgroup_topPanelBlue2;
        int mutedByAdmin = Theme.key_voipgroup_mutedByAdminGradient;
        int mutedByAdmin2 = Theme.key_voipgroup_mutedByAdminGradient2;
        int mutedByAdmin3 = Theme.key_voipgroup_mutedByAdminGradient3;

        private void createGradients() {
            if (currentState == MUTE_BUTTON_STATE_UNMUTE) {
                shader = new RadialGradient(200, 200, 200, new int[]{color1 = Theme.getColor(greenKey1), color2 = Theme.getColor(greenKey2)}, null, Shader.TileMode.CLAMP);
            } else if (currentState == MUTE_BUTTON_STATE_MUTE){
                shader = new RadialGradient(200, 200, 200, new int[]{color1 = Theme.getColor(blueKey1), color2 = Theme.getColor(blueKey2)}, null, Shader.TileMode.CLAMP);
            } else if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                shader = new RadialGradient(200, 200, 200, new int[]{color1 = Theme.getColor(mutedByAdmin),  color3 = Theme.getColor(mutedByAdmin3), color2 = Theme.getColor(mutedByAdmin2)}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            }
        }

        public void update(int height, int width, long dt, float amplitude) {
            if (currentState == MUTE_BUTTON_STATE_CONNECTING) {
                return;
            }
            if (duration == 0 || time >= duration) {
                duration = Utilities.random.nextInt(700) + 500;
                time = 0;
                if (targetX == -1f) {
                    if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                        targetX = -0.3f + 0.05f * Utilities.random.nextInt(100) / 100f;
                        targetY = 0.7f + 0.05f * Utilities.random.nextInt(100) / 100f;
                    } else if (currentState == MUTE_BUTTON_STATE_UNMUTE) {
                        targetX = -0.3f + 0.2f * Utilities.random.nextInt(100) / 100f;
                        targetY = 0.7f + 0.3f * Utilities.random.nextInt(100) / 100f;
                    } else {
                        targetX = 1.1f + 0.2f * (Utilities.random.nextInt(100) / 100f);
                        targetY = 4f * Utilities.random.nextInt(100) / 100f;
                    }
                }
                startX = targetX;
                startY = targetY;
                if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                    targetX = -0.3f + 0.05f * Utilities.random.nextInt(100) / 100f;
                    targetY = 0.7f + 0.05f * Utilities.random.nextInt(100) / 100f;
                } else if (currentState == MUTE_BUTTON_STATE_UNMUTE) {
                    targetX = -0.3f + 0.2f * Utilities.random.nextInt(100) / 100f;
                    targetY = 0.7f + 0.3f * Utilities.random.nextInt(100) / 100f;
                } else {
                    targetX = 1.1f + 0.2f * (Utilities.random.nextInt(100) / 100f);
                    targetY = 4f * Utilities.random.nextInt(100) / 100f;
                }
            }
            time += dt * (0.5f + BlobDrawable.GRADIENT_SPEED_MIN) + dt * (BlobDrawable.GRADIENT_SPEED_MAX * 2) * amplitude;
            if (time > duration) {
                time = duration;
            }
            float interpolation = CubicBezierInterpolator.EASE_OUT.getInterpolation(time / duration);
            float x = width * (startX + (targetX - startX) * interpolation) - 200;
            float y = height * (startY + (targetY - startY) * interpolation) - 200;

            float scale = width / 400.0f * ((currentState == MUTE_BUTTON_STATE_UNMUTE  || currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN)? 3f : 1.5f);
            matrix.reset();
            matrix.postTranslate(x, y);
            matrix.postScale(scale, scale, x + 200, y + 200);

            shader.setLocalMatrix(matrix);
        }

        public void checkColor() {
            if (currentState == MUTE_BUTTON_STATE_UNMUTE) {
                if (color1 != Theme.getColor(greenKey1) || color2 != Theme.getColor(greenKey2)) {
                    createGradients();
                }
            } else if (currentState == MUTE_BUTTON_STATE_MUTE) {
                if (color1 != Theme.getColor(blueKey1) || color2 != Theme.getColor(blueKey2)) {
                    createGradients();
                }
            } else if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                if (color1 != Theme.getColor(mutedByAdmin) || color2 != Theme.getColor(mutedByAdmin2)) {
                    createGradients();
                }
            }
        }

        public void setToPaint(Paint paint) {
            if (currentState == MUTE_BUTTON_STATE_UNMUTE || currentState == MUTE_BUTTON_STATE_MUTE || currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                if (!LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
                    paint.setShader(null);
                    if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                        paint.setColor(ColorUtils.blendARGB(ColorUtils.blendARGB(color1, color2, 0.5f), color3, 0.5f));
                    } else {
                        paint.setColor(ColorUtils.blendARGB(color1, color2, 0.5f));
                    }
                } else {
                    paint.setShader(shader);
                }
            } else {
                paint.setShader(null);
                paint.setColor(Theme.getColor(Theme.key_voipgroup_topPanelGray));
            }
        }
    }
}

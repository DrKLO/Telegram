package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.animation.LinearInterpolator;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;

public class VoiceMessageEnterTransition implements MessageEnterTransitionContainer.Transition {

    private final ChatMessageCell messageView;
    private final RecyclerListView listView;
    float fromRadius;
    float progress;

    final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ValueAnimator animator;
    private final ChatActivityEnterView.RecordCircle recordCircle;
    private final Matrix gradientMatrix;
    private final Paint gradientPaint;
    private final LinearGradient gradientShader;
    private final int messageId;
    MessageEnterTransitionContainer container;
    private final Theme.ResourcesProvider resourcesProvider;

    public VoiceMessageEnterTransition(ChatMessageCell messageView, ChatActivityEnterView chatActivityEnterView, RecyclerListView listView, MessageEnterTransitionContainer container, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        this.messageView = messageView;
        this.container = container;
        this.listView = listView;

        messageView.setEnterTransitionInProgress(true);

        recordCircle = chatActivityEnterView.getRecordCircle();
        if (recordCircle != null) {
            fromRadius = recordCircle.drawingCircleRadius;
            recordCircle.voiceEnterTransitionInProgress = true;
            recordCircle.skipDraw = true;
        }

        gradientMatrix = new Matrix();
        gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        gradientShader = new LinearGradient(0, AndroidUtilities.dp(12), 0, 0, 0, 0xFF000000, Shader.TileMode.CLAMP);
        gradientPaint.setShader(gradientShader);

        messageId = messageView.getMessageObject().stableId;

        container.addTransition(this);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(valueAnimator -> {
            progress = (float) valueAnimator.getAnimatedValue();
            container.invalidate();
        });

        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(220);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                messageView.setEnterTransitionInProgress(false);
                container.removeTransition(VoiceMessageEnterTransition.this);
                if (recordCircle != null) {
                    recordCircle.skipDraw = false;
                }
            }
        });

        if (messageView.getSeekBarWaveform() != null) {
            messageView.getSeekBarWaveform().setSent();
        }
    }

    public void start() {
        animator.start();
    }

    float lastToCx;
    float lastToCy;

    @Override
    public void onDraw(Canvas canvas) {
        float step1Time = 0.6f;
        float moveProgress = progress;
        float hideWavesProgress = progress > step1Time ? 1f : progress / step1Time;

        float fromCx = recordCircle == null ? 0 : recordCircle.drawingCx + recordCircle.getX() - container.getX();
        float fromCy = recordCircle == null ? 0 : recordCircle.drawingCy + recordCircle.getY() - container.getY();

        float toCy;
        float toCx;

        if (messageView.getMessageObject().stableId != messageId) {
            toCx = lastToCx;
            toCy = lastToCy;
        } else {
            toCy = messageView.getRadialProgress().getProgressRect().centerY() + messageView.getY() + listView.getY() - container.getY();
            toCx = messageView.getRadialProgress().getProgressRect().centerX() + messageView.getX() + listView.getX() - container.getX();
        }

        lastToCx = toCx;
        lastToCy = toCy;

        float progress = CubicBezierInterpolator.DEFAULT.getInterpolation(moveProgress);
        float xProgress = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(moveProgress);

        float cx = fromCx * (1f - xProgress) + toCx * xProgress;
        float cy = fromCy * (1f - progress) + toCy * progress;

        float toRadius = messageView.getRadialProgress().getProgressRect().height() / 2;
        float radius = fromRadius * (1f - progress) + toRadius * progress;

        float listViewBottom = listView.getY() - container.getY() + listView.getMeasuredHeight();
        int clipBottom = 0;
        if (container.getMeasuredHeight() > 0) {
            clipBottom = (int) (container.getMeasuredHeight() * (1f - progress) + listViewBottom * progress);
        }
//            canvas.saveLayerAlpha(0, container.getMeasuredHeight() - AndroidUtilities.dp(400), container.getMeasuredWidth(), container.getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
//        } else {
//            canvas.save();
//        }

        final int circleColorKey = messageView.getRadialProgress().getCircleColorKey();
        circlePaint.setColor(ColorUtils.blendARGB(getThemedColor(Theme.key_chat_messagePanelVoiceBackground), getThemedColor(circleColorKey < 0 ? Theme.key_chat_messagePanelVoiceBackground : circleColorKey), progress));

        if (recordCircle != null) {
            recordCircle.drawWaves(canvas, cx, cy, 1f - hideWavesProgress);
        }

        canvas.drawCircle(cx, cy, radius, circlePaint);

        canvas.save();
        float scale = radius / toRadius;
        canvas.scale(scale, scale, cx, cy);
        float tx = cx - messageView.getRadialProgress().getProgressRect().centerX();
        float ty = cy - messageView.getRadialProgress().getProgressRect().centerY();
        canvas.translate(tx, ty);
        messageView.getRadialProgress().setOverrideAlpha(progress);
        messageView.getRadialProgress().setDrawBackground(false);
        messageView.drawVoiceOnce(canvas, progress, () -> {
            messageView.getRadialProgress().draw(canvas);
            canvas.translate(-tx, -ty);
            canvas.scale(1f / scale, 1f / scale, cx, cy);
            if (recordCircle != null) {
                recordCircle.drawIcon(canvas, (int) fromCx, (int) fromCy, 1f - moveProgress);
            }
            canvas.scale(scale, scale, cx, cy);
            canvas.translate(tx, ty);
        });
        messageView.getRadialProgress().setDrawBackground(true);
        messageView.getRadialProgress().setOverrideAlpha(1f);
        canvas.restore();

//        if (container.getMeasuredHeight() > 0) {
//            gradientMatrix.setTranslate(0, clipBottom);
//            gradientShader.setLocalMatrix(gradientMatrix);
//            canvas.drawRect(0, clipBottom, container.getMeasuredWidth(), container.getMeasuredHeight(), gradientPaint);
//        }

        //restore clipRect
//        canvas.restore();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}

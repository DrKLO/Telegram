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
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;

public class VoiceMessageEnterTransition {

    float fromRadius;

    float progress;

    final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ValueAnimator animator;

    public VoiceMessageEnterTransition(FrameLayout containerView, ChatMessageCell messageView, ChatActivityEnterView chatActivityEnterView, RecyclerListView listView) {

        fromRadius = chatActivityEnterView.getRecordCicle().drawingCircleRadius;

        messageView.setVoiceTransitionInProgress(true);

        ChatActivityEnterView.RecordCircle recordCircle = chatActivityEnterView.getRecordCicle();
        chatActivityEnterView.startMessageTransition();
        recordCircle.voiceEnterTransitionInProgress = true;
        recordCircle.skipDraw = true;

        Matrix gradientMatrix = new Matrix();
        Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        LinearGradient gradientShader = new LinearGradient(0, AndroidUtilities.dp(12), 0, 0, 0, 0xFF000000, Shader.TileMode.CLAMP);
        gradientPaint.setShader(gradientShader);

        int messageId = messageView.getMessageObject().stableId;

        View view = new View(containerView.getContext()) {

            float lastToCx;
            float lastToCy;

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                float step1Time = 0.6f;
                float moveProgress = progress;
                float hideWavesProgress = progress > step1Time ? 1f : progress / step1Time;

                float fromCx = recordCircle.drawingCx + recordCircle.getX() - getX();
                float fromCy = recordCircle.drawingCy + recordCircle.getY() - getY();

                float toCy;
                float toCx;

                if (messageView.getMessageObject().stableId != messageId) {
                    toCx = lastToCx;
                    toCy = lastToCy;
                } else {
                    toCy = messageView.getRadialProgress().getProgressRect().centerY() + messageView.getY() + listView.getY() - getY();
                    toCx = messageView.getRadialProgress().getProgressRect().centerX() + messageView.getX() + listView.getX() - getX();
                }

                lastToCx = toCx;
                lastToCy = toCy;

                float progress = CubicBezierInterpolator.DEFAULT.getInterpolation(moveProgress);
                float xProgress = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(moveProgress);

                float cx = fromCx * (1f - xProgress) + toCx * xProgress;
                float cy = fromCy * (1f - progress) + toCy * progress;

                float toRadius = messageView.getRadialProgress().getProgressRect().height() / 2;
                float radius = fromRadius * (1f - progress) + toRadius * progress;

                float listViewBottom = listView.getY() - getY() + listView.getMeasuredHeight();
                int clipBottom = 0;
                if (getMeasuredHeight() > 0) {
                    clipBottom = (int) (getMeasuredHeight() * (1f - progress) + listViewBottom * progress);
                    canvas.saveLayerAlpha(0, getMeasuredHeight() - AndroidUtilities.dp(400), getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
                } else {
                    canvas.save();
                }

                circlePaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground), Theme.getColor(messageView.getRadialProgress().getCircleColorKey()), progress));

                recordCircle.drawWaves(canvas, cx, cy, 1f - hideWavesProgress);

                canvas.drawCircle(cx, cy, radius, circlePaint);

                canvas.save();

                float scale = radius / toRadius;
                canvas.scale(scale, scale, cx, cy);
                canvas.translate(cx - messageView.getRadialProgress().getProgressRect().centerX(), cy - messageView.getRadialProgress().getProgressRect().centerY());

                messageView.getRadialProgress().setOverrideAlpha(progress);
                messageView.getRadialProgress().setDrawBackground(false);
                messageView.getRadialProgress().draw(canvas);
                messageView.getRadialProgress().setDrawBackground(true);
                messageView.getRadialProgress().setOverrideAlpha(1f);
                canvas.restore();

                if (getMeasuredHeight() > 0) {
                    gradientMatrix.setTranslate(0, clipBottom);
                    gradientShader.setLocalMatrix(gradientMatrix);
                    canvas.drawRect(0, clipBottom, getMeasuredWidth(), getMeasuredHeight(), gradientPaint);
                }

                //restore clipRect
                canvas.restore();

                recordCircle.drawIcon(canvas, (int) fromCx, (int) fromCy, 1f - moveProgress);

                recordCircle.skipDraw = false;
                canvas.save();
                canvas.translate(recordCircle.getX() - getX(), recordCircle.getY() - getY());
                recordCircle.draw(canvas);
                canvas.restore();
                recordCircle.skipDraw = true;
            }
        };

        containerView.addView(view);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(valueAnimator -> {
            progress = (float) valueAnimator.getAnimatedValue();
            view.invalidate();
        });

        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(220);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (view.getParent() != null) {
                    messageView.setVoiceTransitionInProgress(false);
                    containerView.removeView(view);
                    recordCircle.skipDraw = false;
                }
            }
        });
    }

    public void start() {
        animator.start();
    }
}

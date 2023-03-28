package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.StateSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class TranscribeButton {

    private final static int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};

    private int backgroundColor, color, iconColor, rippleColor;
    private float backgroundBack;
    private Paint backgroundPaint, strokePaint;
    private Path progressClipPath;

    private boolean loading;
    private AnimatedFloat loadingFloat;

    private int inIconDrawableAlpha;
    private RLottieDrawable inIconDrawable;
    private int outIconDrawableAlpha;
    private RLottieDrawable outIconDrawable;

    private Drawable selectorDrawable;
    private ChatMessageCell parent;
    private SeekBarWaveform seekBar;

    private long start;
    private Rect bounds, pressBounds;
    private boolean clickedToOpen = false;

    private boolean premium;
    private boolean isOpen, shouldBeOpen;

    public TranscribeButton(ChatMessageCell parent, SeekBarWaveform seekBar) {
        start = SystemClock.elapsedRealtime();
        this.parent = parent;
        this.seekBar = seekBar;
        this.bounds = new Rect(0, 0, AndroidUtilities.dp(30), AndroidUtilities.dp(30));
        this.pressBounds = new Rect(this.bounds);
        this.pressBounds.inset(AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        outIconDrawable = new RLottieDrawable(R.raw.transcribe_out, "transcribe_out", AndroidUtilities.dp(26), AndroidUtilities.dp(26));
        outIconDrawable.setCurrentFrame(0);
        outIconDrawable.setCallback(parent);
        outIconDrawable.setOnFinishCallback(() -> {
            outIconDrawable.stop();
            inIconDrawable.stop();
            isOpen = shouldBeOpen = true;
            inIconDrawable.setCurrentFrame(0);
        }, 19);
        outIconDrawable.setAllowDecodeSingleFrame(true);

        inIconDrawable = new RLottieDrawable(R.raw.transcribe_in, "transcribe_in", AndroidUtilities.dp(26), AndroidUtilities.dp(26));
        inIconDrawable.setCurrentFrame(0);
        inIconDrawable.setCallback(parent);
        inIconDrawable.setMasterParent(parent);
        inIconDrawable.setOnFinishCallback(() -> {
            inIconDrawable.stop();
            outIconDrawable.stop();
            isOpen = shouldBeOpen = false;
            outIconDrawable.setCurrentFrame(0);
        }, 19);
        inIconDrawable.setAllowDecodeSingleFrame(true);

        this.isOpen = false;
        this.shouldBeOpen = false;
        premium = parent.getMessageObject() != null && UserConfig.getInstance(parent.getMessageObject().currentAccount).isPremium();

        loadingFloat = new AnimatedFloat(parent, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public void setLoading(boolean loading, boolean animated) {
        this.loading = loading;
        seekBar.setLoading(loading);
        if (!animated) {
            loadingFloat.set(this.loading ? 1 : 0, true);
        } else {
            if (loadingFloat.get() <= 0f) {
                start = SystemClock.elapsedRealtime();
            }
        }
        if (parent != null) {
            parent.invalidate();
        }
    }

    protected void onOpen() {}

    public void setOpen(boolean open, boolean animated) {
        if (!shouldBeOpen && open && clickedToOpen) {
            clickedToOpen = false;
            onOpen();
        }
        boolean wasShouldBeOpen = shouldBeOpen;
        shouldBeOpen = open;
        if (animated) {
            if (open && !wasShouldBeOpen) {
                isOpen = false;
                inIconDrawable.setCurrentFrame(0);
                outIconDrawable.setCurrentFrame(0);
                outIconDrawable.start();
            } else if (!open && wasShouldBeOpen) {
                isOpen = true;
                outIconDrawable.setCurrentFrame(0);
                inIconDrawable.setCurrentFrame(0);
                inIconDrawable.start();
            }
        } else {
            isOpen = open;
            inIconDrawable.stop();
            outIconDrawable.stop();
            inIconDrawable.setCurrentFrame(0);
            outIconDrawable.setCurrentFrame(0);
        }
        if (parent != null) {
            parent.invalidate();
        }
    }

    private boolean pressed = false;
    private long pressId = 0;
    public boolean onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pressed && action == MotionEvent.ACTION_UP) {
                onTap();
                return true;
            }
            pressed = false;
            return false;
        }
        if (!pressBounds.contains((int) x, (int) y)) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            pressed = true;
        }
        if (pressed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && selectorDrawable instanceof RippleDrawable) {
            selectorDrawable.setHotspot(x, y);
            selectorDrawable.setState(pressedState);
            parent.invalidate();
        }
        return true;
    }

    public void onTap() {
        clickedToOpen = false;
        boolean processClick, toOpen = !shouldBeOpen;
        if (!shouldBeOpen) {
            processClick = !loading;
            if (premium && parent.getMessageObject().isSent()) {
                setLoading(true, true);
            }
        } else {
            processClick = true;
            setOpen(false, true);
            setLoading(false, true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && selectorDrawable instanceof RippleDrawable) {
            selectorDrawable.setState(StateSet.NOTHING);
            parent.invalidate();
        }
        pressed = false;
        if (processClick) {
            if (!premium && toOpen) {
                if (parent.getDelegate() != null) {
                    parent.getDelegate().needShowPremiumBulletin(0);
                }
            } else {
                if (toOpen) {
                    clickedToOpen = true;
                }
                transcribePressed(parent.getMessageObject(), toOpen);
            }
        }
    }

    public void drawGradientBackground(Canvas canvas, Rect bounds, float alpha) {

    }

    public void setColor(int color, int grayColor, boolean isOut, float bgBack) {
        boolean disabled = !premium;
//        if (disabled) {
//            color = ColorUtils.blendARGB(color, grayColor, isOut ? .6f : .8f);
//            color = ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * .6f));
//        }
        boolean newColor = this.color != color;
        this.iconColor = this.color = color;
        this.backgroundColor = ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * 0.156f));
        this.backgroundBack = bgBack;
        this.rippleColor = Theme.blendOver(this.backgroundColor, ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * (Theme.isCurrentThemeDark() ? .3f : .2f))));
        if (backgroundPaint == null) {
            backgroundPaint = new Paint();
        }
        backgroundPaint.setColor(this.backgroundColor);
        backgroundPaint.setAlpha((int) (backgroundPaint.getAlpha() * (1f - bgBack)));
        if (newColor || selectorDrawable == null) {
            selectorDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), 0, this.rippleColor);
            selectorDrawable.setCallback(parent);
        }
        if (newColor) {
            inIconDrawable.beginApplyLayerColors();
            inIconDrawable.setLayerColor("Artboard Outlines.**", this.iconColor);
            inIconDrawable.commitApplyLayerColors();
            inIconDrawable.setAllowDecodeSingleFrame(true);
            inIconDrawable.updateCurrentFrame(0, false);
            inIconDrawable.setAlpha(inIconDrawableAlpha = (int) (Color.alpha(color)));
            outIconDrawable.beginApplyLayerColors();
            outIconDrawable.setLayerColor("Artboard Outlines.**", this.iconColor);
            outIconDrawable.commitApplyLayerColors();
            outIconDrawable.setAllowDecodeSingleFrame(true);
            outIconDrawable.updateCurrentFrame(0, false);
            outIconDrawable.setAlpha(outIconDrawableAlpha = (int) (Color.alpha(color)));
        }
        if (strokePaint == null) {
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
        }
        strokePaint.setColor(color);
    }

    private final FastOutSlowInInterpolator interpolator = new FastOutSlowInInterpolator();
    private Path boundsPath;
    private Path loadingPath;
    private int radius, diameter;

    private float a, b;
    public void setBounds(int x, int y, int w, int h, int r) {
        if (w != this.bounds.width() || h != this.bounds.height()) {
            a = (float) (Math.atan((w/2f-r) / (h/2f)) * 180f / Math.PI);
            b = (float) (Math.atan((w/2f) / (h/2f-r)) * 180f / Math.PI);
        }
        this.bounds.set(x, y, x + w, y + h);
        this.radius = Math.min(Math.min(w, h) / 2, r);
        this.diameter = this.radius * 2;
    }
    
    public int width() {
        return this.bounds.width();
    }

    public int height() {
        return this.bounds.height();
    }

    public void draw(Canvas canvas, float alpha) {
        this.pressBounds.set(this.bounds.left - AndroidUtilities.dp(8), this.bounds.top - AndroidUtilities.dp(8), this.bounds.right + AndroidUtilities.dp(8), this.bounds.bottom + AndroidUtilities.dp(8));
        if (boundsPath == null) {
            boundsPath = new Path();
        } else {
            boundsPath.rewind();
        }
        AndroidUtilities.rectTmp.set(this.bounds);
        boundsPath.addRoundRect(AndroidUtilities.rectTmp, this.radius, this.radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(boundsPath);
        if (backgroundBack * alpha > 0) {
            drawGradientBackground(canvas, this.bounds, backgroundBack * alpha);
        }
        if (backgroundPaint != null) {
            int wasAlpha = backgroundPaint.getAlpha();
            backgroundPaint.setAlpha((int) (wasAlpha * alpha));
            canvas.drawRect(this.bounds, backgroundPaint);
            backgroundPaint.setAlpha(wasAlpha);
        }
        if (selectorDrawable != null) {
            selectorDrawable.setBounds(bounds);
            selectorDrawable.draw(canvas);
        }
        canvas.restore();

        float loadingT = loadingFloat.set(loading ? 1f : 0f);
        if (loadingT > 0f) {
            float[] segments = getSegments((long) ((SystemClock.elapsedRealtime() - start) * .75f));

            if (progressClipPath == null) {
                progressClipPath = new Path();
            } else {
                progressClipPath.rewind();
            }
            float segmentLength = Math.max(40 * loadingT, segments[1] - segments[0]);
            float from = segments[0] + segmentLength * (1f - loadingT) * (loading ? 0f : 1f), to = from + segmentLength * loadingT;

            from = from % 360;
            to = to % 360;
            if (from < 0)
                from += 360;
            if (to < 0)
                to += 360;

            addLine(progressClipPath, bounds.centerX(), bounds.top, bounds.right - radius, bounds.top, from, to, 0, a);
            addCorner(progressClipPath, bounds.right, bounds.top, diameter, 1, from, to, a, b);
            addLine(progressClipPath, bounds.right, bounds.top + radius, bounds.right, bounds.bottom - radius, from, to, b, 180 - b);
            addCorner(progressClipPath, bounds.right, bounds.bottom, diameter, 2, from, to, 180 - b, 180 - a);
            addLine(progressClipPath, bounds.right - radius, bounds.bottom, bounds.left + radius, bounds.bottom, from, to, 180 - a, 180 + a);
            addCorner(progressClipPath, bounds.left, bounds.bottom, diameter, 3, from, to, 180 + a, 180 + b);
            addLine(progressClipPath, bounds.left, bounds.bottom - radius, bounds.left, bounds.top + radius, from, to, 180 + b, 360 - b);
            addCorner(progressClipPath, bounds.left, bounds.top, diameter, 4, from, to, 360 - b, 360 - a);
            addLine(progressClipPath, bounds.left + radius, bounds.top,  bounds.centerX(), bounds.top, from, to, 360 - a, 360);

            strokePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
            int wasAlpha = strokePaint.getAlpha();
            strokePaint.setAlpha((int) (wasAlpha * alpha));
            canvas.drawPath(progressClipPath, strokePaint);
            strokePaint.setAlpha(wasAlpha);

            parent.invalidate();
        }

        canvas.save();
        canvas.translate(bounds.centerX() + AndroidUtilities.dp(2 - 15), bounds.centerY() + AndroidUtilities.dp(-1 - 12));
        if (isOpen) {
            inIconDrawable.setAlpha((int) (inIconDrawableAlpha * alpha));
            inIconDrawable.draw(canvas);
        } else {
            outIconDrawable.setAlpha((int) (outIconDrawableAlpha * alpha));
            outIconDrawable.draw(canvas);
        }
        canvas.restore();
    }

    private float[] segments;
    private float[] getSegments(long d) {
        if (segments == null) {
            segments = new float[2];
        }
        final long t = d % 5400L;
        segments[0] = 1520 * t / 5400f - 20;
        segments[1] = 1520 * t / 5400f;
        for (int i = 0; i < 4; ++i) {
            segments[1] += interpolator.getInterpolation((t - i * 1350) / 667f) * 250;
            segments[0] += interpolator.getInterpolation((t - (667 + i * 1350)) / 667f) * 250;
        }
        return segments;
    }

    private void addLine(
        Path path,
        int x1,
        int y1,
        int x2,
        int y2,
        float L, float R,
        float l, float r
    ) {
        if (x1 == x2 && y1 == y2) {
            return;
        }
        if (L > R) {
            addLine(path, x1, y1, x2, y2, (L - l) / (r - l), 1f);
            addLine(path, x1, y1, x2, y2, 0, (R - l) / (r - l));
        } else {
            addLine(path, x1, y1, x2, y2, Math.max(0, L - l) / (r - l), (Math.min(R, r) - l) / (r - l));
        }
    }

    private void addLine(
        Path path,
        int x1,
        int y1,
        int x2,
        int y2,
        float a,
        float b
    ) {
        if (x1 == x2 && y1 == y2) {
            return;
        }
        a = MathUtils.clamp(a, 0, 1);
        b = MathUtils.clamp(b, 0, 1);
        if (b - a <= 0) {
            return;
        }
        path.moveTo(
            AndroidUtilities.lerp(x1, x2, a),
            AndroidUtilities.lerp(y1, y2, a)
        );
        path.lineTo(
            AndroidUtilities.lerp(x1, x2, b),
            AndroidUtilities.lerp(y1, y2, b)
        );
    }

    private void addCorner(
        Path path,
        int x1,
        int y1,
        int d,
        int side,
        float L, float R,
        float l, float r
    ) {
        if (L > R) {
            addCorner(path, x1, y1, d, side, (L - l) / (r - l), 1f);
            addCorner(path, x1, y1, d, side, 0, (R - l) / (r - l));
        } else {
            addCorner(path, x1, y1, d, side, Math.max(0, L - l) / (r - l), (Math.min(R, r) - l) / (r - l));
        }
    }

    private void addCorner(
        Path path,
        int cx, // stands for x of corner, not center
        int cy,
        int d,
        int side,
        float a,
        float b
    ) {
        a = MathUtils.clamp(a, 0, 1);
        b = MathUtils.clamp(b, 0, 1);
        if (b - a <= 0) {
            return;
        }
        if (side == 1) { // top-right
            AndroidUtilities.rectTmp.set(cx-d,cy,cx,cy+d);
        } else if (side == 2) { // bottom-right
            AndroidUtilities.rectTmp.set(cx-d,cy-d,cx,cy);
        } else if (side == 3) { // bottom-left
            AndroidUtilities.rectTmp.set(cx,cy-d,cx+d,cy);
        } else if (side == 4) { // top-left
            AndroidUtilities.rectTmp.set(cx,cy,cx+d,cy+d);
        }
        path.addArc(AndroidUtilities.rectTmp, -180+side*90+(90*a), 90*(b-a));
    }


    public static class LoadingPointsSpan extends ImageSpan {
        private static LoadingPointsDrawable drawable;

        public LoadingPointsSpan() {
            super(drawable == null ? drawable = new LoadingPointsDrawable(Theme.chat_msgTextPaint) : drawable, ImageSpan.ALIGN_BOTTOM);
            float fontSize = Theme.chat_msgTextPaint.getTextSize() * 0.89f;
            int yoff = (int) (fontSize * 0.02f);
            getDrawable().setBounds(0, yoff, (int) fontSize, yoff + (int) (fontSize * 1.25f));
        }

        @Override
        public void updateDrawState(TextPaint textPaint) {
            float fontSize = textPaint.getTextSize() * 0.89f;
            int yoff = (int) (fontSize * 0.02f);
            getDrawable().setBounds(0, yoff, (int) fontSize, yoff + (int) (fontSize * 1.25f));
            super.updateDrawState(textPaint);
        }
    }

    private static class LoadingPointsDrawable extends Drawable {
        private RLottieDrawable lottie;
        private int lastColor;
        private Paint paint;
        public LoadingPointsDrawable(TextPaint textPaint) {
            this.paint = textPaint;
            float fontSize = textPaint.getTextSize() * 0.89f;
            lottie = new RLottieDrawable(R.raw.dots_loading, "dots_loading", (int) fontSize, (int) (fontSize * 1.25f)) {
                @Override
                protected boolean hasParentView() {
                    return true;
                }
            };
            lottie.setAutoRepeat(1);
            lottie.setCurrentFrame((int) (SystemClock.elapsedRealtime() / 16f % 60f));
            lottie.setAllowDecodeSingleFrame(true);
            lottie.start();
        }

        public void setColor(int color) {
            lottie.beginApplyLayerColors();
            lottie.setLayerColor("Comp 1.**", color);
            lottie.commitApplyLayerColors();
            lottie.setAllowDecodeSingleFrame(true);
            lottie.updateCurrentFrame(0, false);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int color = this.paint.getColor();
            if (color != lastColor) {
                setColor(color);
                lastColor = color;
            }
            lottie.draw(canvas);
        }

        @Override
        public void setAlpha(int i) {}
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }


    // requests logic

    private static int reqInfoHash(MessageObject messageObject) {
        if (messageObject == null) {
            return 0;
        }
        return Objects.hash(messageObject.currentAccount, messageObject.getDialogId(), messageObject.getId());
    }

    private static HashMap<Long, MessageObject> transcribeOperationsById;
    private static HashMap<Integer, MessageObject> transcribeOperationsByDialogPosition;
    private static ArrayList<Integer> videoTranscriptionsOpen;

    public static void openVideoTranscription(MessageObject messageObject) {
        if (messageObject == null || isVideoTranscriptionOpen(messageObject)) {
            return;
        }
        if (videoTranscriptionsOpen == null) {
            videoTranscriptionsOpen = new ArrayList<>(1);
        }
        videoTranscriptionsOpen.add(reqInfoHash(messageObject));
    }

    public static boolean isVideoTranscriptionOpen(MessageObject messageObject) {
        return videoTranscriptionsOpen != null && (!messageObject.isRoundVideo() || videoTranscriptionsOpen.contains(reqInfoHash(messageObject)));
    }

    public static void resetVideoTranscriptionsOpen() {
        if (videoTranscriptionsOpen != null) {
            videoTranscriptionsOpen.clear();
        }
    }

    public static boolean isTranscribing(MessageObject messageObject) {
        return (
            (transcribeOperationsByDialogPosition != null && (transcribeOperationsByDialogPosition.containsValue(messageObject) || transcribeOperationsByDialogPosition.containsKey((Integer) reqInfoHash(messageObject)))) ||
            (transcribeOperationsById != null && messageObject != null && messageObject.messageOwner != null && transcribeOperationsById.containsKey(messageObject.messageOwner.voiceTranscriptionId))
        );
    }

    private static void transcribePressed(MessageObject messageObject, boolean open) {
        if (messageObject == null || messageObject.messageOwner == null || !messageObject.isSent()) {
            return;
        }
        int account = messageObject.currentAccount;
        final long start = SystemClock.elapsedRealtime(), minDuration = 350;
        TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(messageObject.messageOwner.peer_id);
        long dialogId = DialogObject.getPeerDialogId(peer);
        int messageId = messageObject.messageOwner.id;
        if (open) {
            if (messageObject.messageOwner.voiceTranscription != null && messageObject.messageOwner.voiceTranscriptionFinal) {
                TranscribeButton.openVideoTranscription(messageObject);
                messageObject.messageOwner.voiceTranscriptionOpen = true;
                MessagesStorage.getInstance(account).updateMessageVoiceTranscriptionOpen(dialogId, messageId, messageObject.messageOwner);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.voiceTranscriptionUpdate, messageObject, null, null, (Boolean) true, (Boolean) true);
                });
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("sending Transcription request, msg_id=" + messageId + " dialog_id=" + dialogId);
                }
                TLRPC.TL_messages_transcribeAudio req = new TLRPC.TL_messages_transcribeAudio();
                req.peer = peer;
                req.msg_id = messageId;
                if (transcribeOperationsByDialogPosition == null) {
                    transcribeOperationsByDialogPosition = new HashMap<>();
                }
                transcribeOperationsByDialogPosition.put((Integer) reqInfoHash(messageObject), messageObject);
                ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> {
                    String text;
                    long id = 0;
                    boolean isFinal = false;
                    if (res instanceof TLRPC.TL_messages_transcribedAudio) {
                        TLRPC.TL_messages_transcribedAudio r = (TLRPC.TL_messages_transcribedAudio) res;
                        text = r.text;
                        id = r.transcription_id;
                        isFinal = !r.pending;
                        if (TextUtils.isEmpty(text)) {
                            text = !isFinal ? null : "";
                        }
                        if (transcribeOperationsById == null) {
                            transcribeOperationsById = new HashMap<>();
                        }
                        transcribeOperationsById.put(id, messageObject);
                        messageObject.messageOwner.voiceTranscriptionId = id;
                    } else {
                        text = "";
                        isFinal = true;
                    }
                    final String finalText = text;
                    final long finalId = id;
                    final long duration = SystemClock.elapsedRealtime() - start;
                    TranscribeButton.openVideoTranscription(messageObject);
                    messageObject.messageOwner.voiceTranscriptionOpen = true;
                    messageObject.messageOwner.voiceTranscriptionFinal = isFinal;
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("Transcription request sent, received final=" + isFinal + " id=" + finalId + " text=" + finalText);
                    }

                    MessagesStorage.getInstance(account).updateMessageVoiceTranscription(dialogId, messageId, finalText, messageObject.messageOwner);
                    if (isFinal) {
                        AndroidUtilities.runOnUIThread(() -> finishTranscription(messageObject, finalId, finalText), Math.max(0, minDuration - duration));
                    }
                });
            }
        } else {
            if (transcribeOperationsByDialogPosition != null) {
                transcribeOperationsByDialogPosition.remove((Integer) reqInfoHash(messageObject));
            }
            messageObject.messageOwner.voiceTranscriptionOpen = false;
            MessagesStorage.getInstance(account).updateMessageVoiceTranscriptionOpen(dialogId, messageId, messageObject.messageOwner);
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.voiceTranscriptionUpdate, messageObject, null, null, (Boolean) false, null);
            });
        }
    }

    public static boolean finishTranscription(MessageObject messageObject, long transcription_id, String text) {
        try {
            MessageObject messageObjectByTranscriptionId = null;
            if (transcribeOperationsById != null && transcribeOperationsById.containsKey(transcription_id)) {
                messageObjectByTranscriptionId = transcribeOperationsById.remove(transcription_id);
            }
            if (messageObject == null) {
                messageObject = messageObjectByTranscriptionId;
            }
            if (messageObject == null || messageObject.messageOwner == null) {
                return false;
            }
            final MessageObject finalMessageObject = messageObject;
            if (transcribeOperationsByDialogPosition != null) {
                transcribeOperationsByDialogPosition.remove((Integer) reqInfoHash(messageObject));
            }
            messageObject.messageOwner.voiceTranscriptionFinal = true;
            MessagesStorage.getInstance(messageObject.currentAccount).updateMessageVoiceTranscription(messageObject.getDialogId(), messageObject.getId(), text, messageObject.messageOwner);
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(finalMessageObject.currentAccount).postNotificationName(NotificationCenter.voiceTranscriptionUpdate, finalMessageObject, (Long) transcription_id, (String) text, (Boolean) true, (Boolean) true);
            });
            return true;
        } catch (Exception ignore) {}
        return false;
    }

    public static void showOffTranscribe(MessageObject messageObject) {
        showOffTranscribe(messageObject, true);
    }

    public static void showOffTranscribe(MessageObject messageObject, boolean notify) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }
        final MessageObject finalMessageObject = messageObject;
        messageObject.messageOwner.voiceTranscriptionForce = true;
        MessagesStorage.getInstance(messageObject.currentAccount).updateMessageVoiceTranscriptionOpen(messageObject.getDialogId(), messageObject.getId(), messageObject.messageOwner);
        if (notify) {
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(finalMessageObject.currentAccount).postNotificationName(NotificationCenter.voiceTranscriptionUpdate, finalMessageObject);
            });
        }
    }
}

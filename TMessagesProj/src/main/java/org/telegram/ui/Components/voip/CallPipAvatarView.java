package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BlobDrawable;

/** Avatar on a blurred copy of itself, with voice waves while the peer is speaking (call PiP). */
final class CallPipAvatarView extends View {
    /** Waves are generated for an avatar of this size and scaled: tiles resize on every frame. */
    private static final float REFERENCE_SIZE = 100f;

    private final ImageReceiver avatarImage = new ImageReceiver(this);
    private final ImageReceiver backgroundImage = new ImageReceiver(this);
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final BlobDrawable tinyWave = new BlobDrawable(9);
    private final BlobDrawable bigWave = new BlobDrawable(12);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();

    private float amplitude, targetAmplitude;
    private float speakingProgress;
    private boolean speaking;
    private long lastFrameTime;

    CallPipAvatarView(Context context) {
        super(context);
        dimPaint.setColor(0x80000000);
        wavePaint.setColor(Theme.getColor(Theme.key_voipgroup_speakingText));
        // proportions of the grid's no-video stub: waves 76..95dp around a 157dp avatar
        tinyWave.minRadius = REFERENCE_SIZE * 0.48f;
        tinyWave.maxRadius = REFERENCE_SIZE * 0.59f;
        bigWave.minRadius = REFERENCE_SIZE * 0.51f;
        bigWave.maxRadius = REFERENCE_SIZE * 0.61f;
        tinyWave.generateBlob();
        bigWave.generateBlob();
    }

    void setPeer(int account, TLObject object) {
        avatarDrawable.setInfo(account, object);
        avatarImage.setForUserOrChat(object, avatarDrawable);
        final ImageLocation location;
        if (object instanceof TLRPC.User) {
            location = ImageLocation.getForUser((TLRPC.User) object, ImageLocation.TYPE_SMALL);
        } else if (object instanceof TLRPC.Chat) {
            location = ImageLocation.getForChat((TLRPC.Chat) object, ImageLocation.TYPE_SMALL);
        } else {
            location = null;
        }
        backgroundImage.setImage(location, "50_50_b", new android.graphics.drawable.ColorDrawable(Theme.getColor(Theme.key_voipgroup_listViewBackground)), null, object, 0);
        invalidate();
    }

    /** {@code level} is the participant's audio level as reported by the call (0..~1). */
    void setSpeaking(boolean speaking, float level) {
        this.speaking = speaking;
        targetAmplitude = Math.min(1f, Math.max(0f, level * 15f / 80f));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        backgroundImage.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.onDetachedFromWindow();
        backgroundImage.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final int w = getWidth(), h = getHeight();
        backgroundImage.setImageCoords(0, 0, w, h);
        backgroundImage.draw(canvas);
        canvas.drawRect(0, 0, w, h, dimPaint);

        final long now = SystemClock.uptimeMillis();
        final long dt = lastFrameTime == 0 ? 16 : Math.min(48, now - lastFrameTime);
        lastFrameTime = now;
        amplitude += (targetAmplitude - amplitude) * Math.min(1f, dt / 120f);
        speakingProgress += ((speaking ? 1f : 0f) - speakingProgress) * Math.min(1f, dt / 200f);

        final float size = Math.min(w, h) * 0.42f;
        final float cx = w / 2f, cy = h / 2f - Math.min(w, h) * 0.04f;
        if (speakingProgress > 0.01f) {
            bigWave.update(amplitude, 1f);
            tinyWave.update(amplitude, 1f);
            final float scale = (1f + 0.8f * amplitude) * size / REFERENCE_SIZE;
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(scale, scale);
            wavePaint.setAlpha((int) (76 * speakingProgress));
            bigWave.draw(0, 0, canvas, wavePaint);
            wavePaint.setAlpha((int) (110 * speakingProgress));
            tinyWave.draw(0, 0, canvas, wavePaint);
            canvas.restore();
        }
        final float avatarScale = 1f + 0.2f * amplitude * speakingProgress;
        avatarImage.setRoundRadius((int) (size / 2));
        avatarImage.setImageCoords(cx - size / 2, cy - size / 2, size, size);
        canvas.save();
        canvas.scale(avatarScale, avatarScale, cx, cy);
        avatarImage.draw(canvas);
        canvas.restore();

        if (speakingProgress > 0.01f || Math.abs(targetAmplitude - amplitude) > 0.005f) {
            invalidate();
        } else {
            lastFrameTime = 0;
        }
    }
}

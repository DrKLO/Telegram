package org.telegram.ui.Components.poll;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

import me.vkryl.android.animator.BoolAnimator;

@SuppressLint("ViewConstructor")
public class PollAttachButton extends View {
    public final Drawable attachDrawable;
    private final Theme.ResourcesProvider resourcesProvider;
    private final BoolAnimator animatorHasMedia = new BoolAnimator(this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private final int size;
    private PollAttachedMedia attachedMedia;

    public PollAttachButton(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, 38);
    }

    public PollAttachButton(Context context, Theme.ResourcesProvider resourcesProvider, int size) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.size = size;

        attachDrawable = context.getResources().getDrawable(R.drawable.outline_poll_attach_24).mutate();
        attachDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_pollCreateIcons), PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        final int ds = dp(24);
        final int dx = (w - ds) / 2;
        final int dy = (h - ds) / 2;
        attachDrawable.setBounds(dx, dy, dx + ds, dy + ds);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (attachedMedia != null) {
            attachedMedia.attach(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (attachedMedia != null) {
            attachedMedia.detach();
        }
    }

    public void setAttachedMedia(PollAttachedMedia media, boolean animated) {
        animatorHasMedia.setValue(media != null, animated);
        if (isAttachedToWindow() && attachedMedia != null) {
            attachedMedia.detach();
        }

        attachedMedia = media;
        if (isAttachedToWindow() && attachedMedia != null) {
            attachedMedia.attach(this);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        final float cx = getWidth() / 2f;
        final float cy = getHeight() / 2f;
        final float hasMedia = animatorHasMedia.getFloatValue();

        if (hasMedia < 1) {
            canvas.save();
            canvas.scale(1f - hasMedia, 1f - hasMedia, cx, cy);
            attachDrawable.draw(canvas);
            canvas.restore();
        }
        if (hasMedia > 0) {
            final int rs = dp(size);
            final int rx = (getWidth() - rs) / 2;
            final int ry = (getHeight() - rs) / 2;

            canvas.save();
            canvas.translate(rx, ry);
            canvas.scale(hasMedia, hasMedia, dp(size) / 2f, dp(size) / 2f);
            if (attachedMedia != null) {
                attachedMedia.draw(canvas, dp(size), dp(size));
            }
            canvas.restore();
        }
    }
}

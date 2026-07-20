package org.telegram.ui.Components.poll.attached;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.PorterDuffColorFilterState;
import org.telegram.ui.Components.poll.PollAttachedMedia;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class PollAttachedMediaLink extends PollAttachedMedia implements Drawable.Callback, FactorAnimator.Target {
    public final String url;

    private final PorterDuffColorFilterState colorFilterState = new PorterDuffColorFilterState();
    private final Drawable drawable;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final CircularProgressDrawable progressDrawable = new CircularProgressDrawable();
    private View attachedTo;
    private TLRPC.WebPage webPage;

    private final BoolAnimator animatorProgress = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);
    private final BoolAnimator animatorHasImage = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

    public PollAttachedMediaLink(String url) {
        this.url = url;

        imageReceiver.setRoundRadius(dp(7));
        drawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.media_link_24).mutate();
        progressDrawable.setCallback(this);
        progressDrawable.setColor(Theme.getColor(Theme.key_pollCreateIcons));
        progressDrawable.size = dp(15);
    }

    public TLRPC.WebPage getWebPage() {
        return webPage;
    }

    public void setWebPage(TLRPC.WebPage webPage, boolean progress, boolean animated) {
        final boolean progressToSet = progress || webPage instanceof TLRPC.TL_webPagePending;
        animatorProgress.setValue(progressToSet, animated);

        this.webPage = webPage;
        if (webPage != null && webPage.photo != null) {
            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(webPage.photo.sizes, 40);
            final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(webPage.photo.sizes, dp(36),
                    false, thumb , true);

            imageReceiver.setImage(
                    ImageLocation.getForObject(size, webPage.photo), "48_48",
                    ImageLocation.getForObject(thumb, webPage.photo), "48_48_b", 0, null, webPage, 1);
            animatorHasImage.setValue(true, animated);
        } else {
            animatorHasImage.setValue(false, animated);
            imageReceiver.clearImage();
        }
    }

    @Override
    public void attach(View parent) {
        super.attach(parent);
        attachedTo = parent;
    }

    @Override
    public void detach() {
        super.detach();
        attachedTo = null;
    }

    @Override
    protected void draw(Canvas canvas, int w, int h) {
        imageReceiver.setImageCoords(0, 0, w, h);
        imageReceiver.draw(canvas);

        progressDrawable.setBounds(0, 0, w, h);
        paint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundGray), 0x40000000, animatorHasImage.getFloatValue()));
        canvas.drawRoundRect(0, 0, w, h, dp(7), dp(7), paint);

        drawable.setColorFilter(colorFilterState.get(ColorUtils.blendARGB(Theme.getColor(Theme.key_pollCreateIcons), 0xFFFFFFFF, animatorHasImage.getFloatValue()), PorterDuff.Mode.SRC_IN));
        DrawableUtils.setBounds(drawable, w / 2f, h / 2f, dp(24), dp(24), Gravity.CENTER);
        DrawableUtils.drawWithScale(canvas, drawable, 1f - animatorProgress.getFloatValue());
        DrawableUtils.drawWithScale(canvas, progressDrawable, animatorProgress.getFloatValue());
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        if (attachedTo != null) {
            attachedTo.invalidate();
        }
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {

    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {

    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (attachedTo != null) {
            attachedTo.invalidate();
        }
    }
}

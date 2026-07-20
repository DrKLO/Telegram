package org.telegram.ui.Components.poll.attached;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ClipRoundedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.poll.PollAttachedMedia;

public class PollAttachedMediaLocation extends PollAttachedMedia {
    public final TLRPC.MessageMedia media;

    public PollAttachedMediaLocation(TLRPC.MessageMedia location) {         // venue or geo
        this.media = location;
        imageReceiver.setRoundRadius(dp(7));
        setupImageReceiver(imageReceiver);
    }

    private void setupImageReceiver(ImageReceiver imageReceiver) {
        final TLRPC.GeoPoint point = media.geo;
        if (point == null) {
            imageReceiver.clearImage();
            return;
        }

        WebFile currentWebFile = WebFile.createWithGeoPoint(point, 38, 38, 13, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));
        imageReceiver.setImage(ImageLocation.getForWebFile(currentWebFile), (String) null, (ImageLocation) null, null, (Drawable) null, null, 0);
    }

    @Override
    protected void draw(Canvas canvas, int w, int h) {
        imageReceiver.setImageCoords(0, 0, w, h);
        imageReceiver.draw(canvas);
    }



    public Drawable createMessagePreviewDrawable(View view) {
        final ImageReceiver imageReceiver = new ImageReceiver(view);

        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(R.raw.map_placeholder, Theme.key_chat_outLocationIcon, (Theme.isCurrentThemeDark() ? 3 : 6) * .12f);
        svgThumb.setAspectCenter(true);
        svgThumb.setColorKey(Theme.key_chat_inLocationIcon);
        ClipRoundedDrawable locationLoadingThumb = new ClipRoundedDrawable(svgThumb);

        WebFile currentWebFile = WebFile.createWithGeoPoint(media.geo, 300, 300 * 9 / 16, 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));
        imageReceiver.setImage(ImageLocation.getForWebFile(currentWebFile), null, null, null, locationLoadingThumb, null, 0);

        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                imageReceiver.onAttachedToWindow();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                imageReceiver.onDetachedFromWindow();
            }
        });
        imageReceiver.setRoundRadius(dp(14));


        Drawable redLocationIcon = view.getContext().getResources().getDrawable(R.drawable.map_pin).mutate();
        return new Drawable() {
            @Override
            public void draw(@NonNull Canvas canvas) {
                imageReceiver.draw(canvas);

                int w = (int) (redLocationIcon.getIntrinsicWidth() * 0.8f);
                int h = (int) (redLocationIcon.getIntrinsicHeight() * 0.8f);
                int x = (int) (imageReceiver.getImageX() + (imageReceiver.getImageWidth() - w) / 2);
                int y = (int) (imageReceiver.getImageY() + (imageReceiver.getImageHeight() / 2 - h) - dp(16) * (1f - CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(imageReceiver.getCurrentAlpha())));
                redLocationIcon.setAlpha((int) (255 * Math.min(1, imageReceiver.getCurrentAlpha() * 5) * imageReceiver.getAlpha()));
                redLocationIcon.setBounds(x, y, x + w, y + h);
                redLocationIcon.draw(canvas);
            }

            @Override
            public void setAlpha(int alpha) {
                imageReceiver.setAlpha(alpha / 255f);
            }

            @Override
            public int getAlpha() {
                return (int) (imageReceiver.getAlpha() * 255);
            }

            @Override
            protected void onBoundsChange(@NonNull Rect bounds) {
                imageReceiver.setImageCoords(
                    bounds.left + dp(2),
                    bounds.top + dp(2),
                    (bounds.right - dp(2)) - (bounds.left + dp(2)),
                    (bounds.bottom - dp(2)) - (bounds.top + dp(2))
                );
            }

            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {

            }

            @Override
            public int getOpacity() {
                return PixelFormat.UNKNOWN;
            }
        };
    }
}

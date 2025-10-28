package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.PhotoViewer;

public class EditCoverButton extends View {

    private final Text text;
    private final PhotoViewerBlurDrawable blur;
    private final Drawable arrowDrawable;

    private ImageReceiver imageReceiver;

    private final ButtonBounce bounce = new ButtonBounce(this);
    private final RectF imageBounds = new RectF();

    public EditCoverButton(Context context, PhotoViewer photoViewer, CharSequence text, boolean withArrow) {
        super(context);

        imageReceiver = new ImageReceiver(this);
        imageReceiver.setRoundRadius(dp(22.66f));

        this.text = new Text(text, 14, AndroidUtilities.bold());
        this.blur = new PhotoViewerBlurDrawable(photoViewer, photoViewer.blurManager, this).setApplyBounds(false);
        if (withArrow) {
            this.arrowDrawable = context.getResources().getDrawable(R.drawable.arrow_newchat).mutate();
            this.arrowDrawable.setColorFilter(new PorterDuffColorFilter(0x99FFFFFF, PorterDuff.Mode.SRC_IN));
        } else {
            this.arrowDrawable = null;
        }

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }

    public void setImage(Bitmap bitmap) {
        this.imageReceiver.setImageBitmap(bitmap);
        invalidate();
    }

    public void setImage(TLRPC.Photo photo, Object parentObject) {
        if (photo == null) {
            setImage((Bitmap) null);
            return;
        }
        final TLRPC.PhotoSize size1 = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, dp(48), false, null, true);
        final TLRPC.PhotoSize size2 = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, dp(24), false, size1, false);
        this.imageReceiver.setImage(ImageLocation.getForPhoto(size1, photo), "24_24", ImageLocation.getForPhoto(size2, photo), "24_24", 0, null, parentObject, 0);
    }

    public void setImage(String path) {
        if (path == null) {
            setImage((Bitmap) null);
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            final Bitmap frame = BitmapFactory.decodeFile(path);
            final Bitmap bitmap = Bitmap.createBitmap(dp(26), dp(26), Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.translate(bitmap.getWidth() / 2.0f, bitmap.getHeight() / 2.0f);
            final float scale = Math.max((float) bitmap.getWidth() / frame.getWidth(), (float) bitmap.getHeight() / frame.getHeight());
            canvas.scale(scale, scale);
            canvas.drawBitmap(frame, -frame.getWidth() / 2.0f, -frame.getHeight() / 2.0f, paint);

            AndroidUtilities.runOnUIThread(() -> setImage(frame));
        });
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final float scale = bounce.getScale(0.05f);
        canvas.save();
        canvas.scale(scale, scale, getWidth() / 2.0f, getHeight() / 2.0f);

        final boolean hasImage = imageReceiver.hasBitmapImage();
        final int leftPadding = (hasImage ? dp(30.33f) : dp(11.33f));
        final int width = leftPadding + (int) Math.ceil(text.getCurrentWidth()) + dp(19);
        final int height = dp(24);
        final int left = (getWidth() - width) / 2, cy = getHeight() / 2;

        blur.setBounds(left, cy - height / 2, left + width, cy + height / 2);
        blur.draw(canvas);

        if (hasImage) {
            imageBounds.set(left + dp(.66f), cy - dp(22.66f) / 2, left + dp(.66f + 22.66f), cy + dp(22.66f) / 2);
            imageReceiver.setImageCoords(imageBounds);
            imageReceiver.draw(canvas);
        }

        text.draw(canvas, left + leftPadding, cy, 0xFFFFFFFF, 1.0f);

        arrowDrawable.setBounds(left + width - dp(17), cy - dp(6), left + width - dp(5), cy + dp(6));
        arrowDrawable.draw(canvas);

        canvas.restore();
    }

    private OnClickListener listener;
    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        listener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final boolean hit = blur.getBounds().contains((int) event.getX(), (int) event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            bounce.setPressed(hit);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!hit) {
                bounce.setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (bounce.isPressed()) {
                bounce.setPressed(false);
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (bounce.isPressed()) {
                bounce.setPressed(false);
                if (listener != null) {
                    listener.onClick(this);
                }
                return true;
            }
        }
        return bounce.isPressed();
    }
}

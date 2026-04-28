package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

@SuppressLint("ViewConstructor")
public class EditCoverButton extends View {

    private final Text text;
    private final Drawable arrowDrawable;
    private final ImageReceiver imageReceiver;
    private final Rect bounds = new Rect();

    private final RectF imageBounds = new RectF();

    private BlurredBackgroundDrawable blurredBackgroundDrawable;

    public EditCoverButton(Context context, CharSequence text, boolean withArrow) {
        super(context);

        imageReceiver = new ImageReceiver(this);
        imageReceiver.setRoundRadius(dp(22.66f));

        this.text = new Text(text, 14, AndroidUtilities.bold());
        if (withArrow) {
            this.arrowDrawable = context.getResources().getDrawable(R.drawable.arrow_newchat).mutate();
            this.arrowDrawable.setColorFilter(new PorterDuffColorFilter(0x99FFFFFF, PorterDuff.Mode.SRC_IN));
        } else {
            this.arrowDrawable = null;
        }

    }

    public void setBlurredBackgroundDrawable(BlurredBackgroundDrawable blurredBackgroundDrawable) {
        this.blurredBackgroundDrawable = blurredBackgroundDrawable
            .setPadding(dp(4))
            .setRadius(dp(11));
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
        final boolean hasImage = imageReceiver.hasBitmapImage();
        final int leftPadding = (hasImage ? dp(30.33f) : dp(11.33f));
        final int width = leftPadding + (int) Math.ceil(text.getCurrentWidth()) + dp(19);
        final int height = dp(24);
        final int left = (getWidth() - width) / 2, cy = getHeight() / 2, top = cy - height / 2;

        bounds.set(left, top, left + width, top + height);
        bounds.inset(-dp(4), -dp(4));

        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.setBounds(bounds);
            blurredBackgroundDrawable.draw(canvas);
        }

        if (hasImage) {
            imageBounds.set(left + dp(.66f), cy - dp(22.66f) / 2f, left + dp(.66f + 22.66f), cy + dp(22.66f) / 2f);
            imageReceiver.setImageCoords(imageBounds);
            imageReceiver.draw(canvas);
        }

        text.draw(canvas, left + leftPadding, cy, 0xFFFFFFFF, 1.0f);

        arrowDrawable.setBounds(left + width - dp(17), cy - dp(6), left + width - dp(5), cy + dp(6));
        arrowDrawable.draw(canvas);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final boolean hit = bounds.contains((int) event.getX(), (int) event.getY());
        if (!hit && event.getAction() == MotionEvent.ACTION_DOWN) {
            return false;
        }

        return super.dispatchTouchEvent(event);
    }
}

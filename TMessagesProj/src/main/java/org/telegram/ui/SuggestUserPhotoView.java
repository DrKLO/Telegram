package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.PhotoCropView;

public class SuggestUserPhotoView extends View {

    ImageReceiver currentPhoto = new ImageReceiver(this);
    ImageReceiver newPhoto = new ImageReceiver(this);
    AvatarDrawable avatarDrawable = new AvatarDrawable();
    View containterView;
    PhotoCropView photoCropView;
    Path path = new Path();
    Drawable arrowDrawable;

    public SuggestUserPhotoView(Context context) {
        super(context);
        avatarDrawable.setInfo(UserConfig.selectedAccount, UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser());
        currentPhoto.setForUserOrChat(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser(), avatarDrawable);
        newPhoto.setForUserOrChat(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser(), avatarDrawable);
        arrowDrawable = ContextCompat.getDrawable(context, R.drawable.msg_arrow_avatar);
        arrowDrawable.setAlpha(100);
    }


    @Override
    public void draw(Canvas canvas) {
        int centerX = getMeasuredWidth() >> 1;
        int cy = getMeasuredHeight() - AndroidUtilities.dp(30);
        int cx1 = centerX - AndroidUtilities.dp(46);
        int cx2 = centerX + AndroidUtilities.dp(46);
        setImageCoords(currentPhoto, cx1, cy);
        setImageCoords(newPhoto, cx2, cy);

        arrowDrawable.setBounds(
                centerX - arrowDrawable.getIntrinsicWidth() / 2,
                cy - arrowDrawable.getIntrinsicHeight() / 2,
                centerX + arrowDrawable.getIntrinsicWidth() / 2,
                cy + arrowDrawable.getIntrinsicHeight() / 2
        );

        arrowDrawable.draw(canvas);
        path.reset();
        path.addCircle(cx2, cy, AndroidUtilities.dp(30), Path.Direction.CW);

        currentPhoto.draw(canvas);

        if (containterView != null) {
            float topOffset = 0, leftOffset = 0;
            topOffset -= photoCropView.getTop();
            leftOffset -= photoCropView.getLeft();
            float s = AndroidUtilities.dp(60) / (float) photoCropView.cropView.areaView.size;
            topOffset -= photoCropView.cropView.areaView.top;
            leftOffset -= photoCropView.cropView.areaView.left;
            canvas.save();
            canvas.clipPath(path);
            canvas.scale(s, s, 0, 0);
            canvas.translate(leftOffset, topOffset);
            canvas.translate((cx2 - AndroidUtilities.dp(30)) / s, (cy - AndroidUtilities.dp(30)) / s);

            PhotoViewer.getInstance().skipLastFrameDraw = true;
            containterView.draw(canvas);
            PhotoViewer.getInstance().skipLastFrameDraw = false;
            canvas.restore();
        }
        super.draw(canvas);
        containterView.invalidate();
        invalidate();
    }

    private void setImageCoords(ImageReceiver currentPhoto, int cx1, int cy) {
        currentPhoto.setImageCoords(cx1 - AndroidUtilities.dp(30), cy - AndroidUtilities.dp(30), AndroidUtilities.dp(60), AndroidUtilities.dp(60));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        currentPhoto.setRoundRadius(AndroidUtilities.dp(30));
        newPhoto.setRoundRadius(AndroidUtilities.dp(30));
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        currentPhoto.onAttachedToWindow();
        newPhoto.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        currentPhoto.onDetachedFromWindow();
        newPhoto.onDetachedFromWindow();
    }


    public void setImages(TLObject setAvatarFor, View containerView, PhotoCropView photoCropView) {
        avatarDrawable.setInfo(setAvatarFor);
        currentPhoto.setForUserOrChat(setAvatarFor, avatarDrawable);
        this.containterView = containerView;
        this.photoCropView = photoCropView;
    }
}

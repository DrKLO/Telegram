package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

public class PinchToZoomHelper {

    private final ViewGroup parentView;
    private final ViewGroup fragmentView;

    private ZoomOverlayView overlayView;
    private View child;
    private ImageReceiver childImage;

    private ImageReceiver fullImage = new ImageReceiver();
    private ImageReceiver blurImage = new ImageReceiver();
    private boolean hasMediaSpoiler;
    private SpoilerEffect mediaSpoilerEffect = new SpoilerEffect();
    private Path path = new Path();
    private float[] spoilerRadii = new float[8];

    private boolean inOverlayMode;

    float parentOffsetX;
    float parentOffsetY;

    float fragmentOffsetX;
    float fragmentOffsetY;

    float pinchCenterX;
    float pinchCenterY;

    private float imageX;
    private float imageY;
    private float imageHeight;
    private float imageWidth;

    private float fullImageHeight;
    private float fullImageWidth;

    private float finishProgress;
    private float progressToFullView;
    ValueAnimator finishTransition;
    private MessageObject messageObject;

    Callback callback;
    ClipBoundsListener clipBoundsListener;

    float pinchStartCenterX;
    float pinchStartCenterY;
    float pinchStartDistance;
    float pinchTranslationX;
    float pinchTranslationY;
    boolean isInPinchToZoomTouchMode;

    private int pointerId1, pointerId2;

    float pinchScale;

    private float enterProgress;
    private float[] clipTopBottom = new float[2];

    private boolean isHardwareVideo;

    public PinchToZoomHelper(ViewGroup parentView, ViewGroup fragmentView) {
        this.parentView = parentView;
        this.fragmentView = fragmentView;
    }

    public void startZoom(View child, ImageReceiver image, MessageObject messageObject) {
        this.child = child;
        this.messageObject = messageObject;

        if (overlayView == null) {
            overlayView = new ZoomOverlayView(parentView.getContext());
            overlayView.setFocusable(false);
            overlayView.setFocusableInTouchMode(false);
            overlayView.setEnabled(false);
        }

        if (fullImage == null) {
            fullImage = new ImageReceiver();
            fullImage.setCrossfadeAlpha((byte) 2);
            fullImage.setCrossfadeWithOldImage(false);
            fullImage.onAttachedToWindow();

            blurImage = new ImageReceiver();
            blurImage.setCrossfadeAlpha((byte) 2);
            blurImage.setCrossfadeWithOldImage(false);
            blurImage.onAttachedToWindow();
        }

        inOverlayMode = true;
        parentView.addView(overlayView);
        finishProgress = 1f;
        progressToFullView = 0f;

        hasMediaSpoiler = messageObject != null && messageObject.hasMediaSpoilers() && !messageObject.isMediaSpoilersRevealed;
        if (blurImage.getBitmap() != null) {
            blurImage.getBitmap().recycle();
            blurImage.setImageBitmap((Bitmap) null);
        }

        if (image.getBitmap() != null && !image.getBitmap().isRecycled() && hasMediaSpoiler) {
            blurImage.setImageBitmap(Utilities.stackBlurBitmapMax(image.getBitmap()));
        }

        setFullImage(messageObject);

        imageX = image.getImageX();
        imageY = image.getImageY();
        imageHeight = image.getImageHeight();
        imageWidth = image.getImageWidth();
        fullImageHeight = image.getBitmapHeight();
        fullImageWidth = image.getBitmapWidth();

        if (fullImageHeight / fullImageWidth != imageHeight / imageWidth) {
            if (fullImageHeight / fullImageWidth < imageHeight / imageWidth) {
                fullImageWidth = fullImageWidth / fullImageHeight * imageHeight;
                fullImageHeight = imageHeight;
            } else {
                fullImageHeight = fullImageHeight / fullImageWidth * imageWidth;
                fullImageWidth = imageWidth;
            }
        } else {
            fullImageHeight = imageHeight;
            fullImageWidth = imageWidth;
        }


        if (messageObject != null && messageObject.isVideo() && MediaController.getInstance().isPlayingMessage(messageObject)) {
            isHardwareVideo = true;
            MediaController.getInstance().setTextureView(overlayView.videoTextureView, overlayView.aspectRatioFrameLayout, overlayView.videoPlayerContainer, true);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) overlayView.videoPlayerContainer.getLayoutParams();
            overlayView.videoPlayerContainer.setTag(R.id.parent_tag, image);
            if (layoutParams.width != image.getImageWidth() || layoutParams.height != image.getImageHeight()) {
                overlayView.aspectRatioFrameLayout.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                layoutParams.width = (int) image.getImageWidth();
                layoutParams.height = (int) image.getImageHeight();
                overlayView.videoPlayerContainer.setLayoutParams(layoutParams);
            }
            overlayView.videoTextureView.setScaleX(1f);
            overlayView.videoTextureView.setScaleY(1f);

            if (callback != null) {
                overlayView.backupImageView.setImageBitmap(callback.getCurrentTextureView().getBitmap((int) fullImageWidth, (int) fullImageHeight));
                overlayView.backupImageView.setSize((int) fullImageWidth, (int) fullImageHeight);
                overlayView.backupImageView.getImageReceiver().setRoundRadius(image.getRoundRadius());
            }
            overlayView.videoPlayerContainer.setVisibility(View.VISIBLE);
        } else {
            isHardwareVideo = false;
            this.childImage = new ImageReceiver();
            this.childImage.onAttachedToWindow();
            Drawable drawable = image.getDrawable();
            this.childImage.setImageBitmap(drawable);
            if (drawable instanceof AnimatedFileDrawable) {
                ((AnimatedFileDrawable) drawable).addSecondParentView(overlayView);
                ((AnimatedFileDrawable) drawable).setInvalidateParentViewWithSecond(true);
            }
            this.childImage.setImageCoords(imageX, imageY, imageWidth, imageHeight);
            this.childImage.setRoundRadius(image.getRoundRadius());

            this.fullImage.setRoundRadius(image.getRoundRadius());
            overlayView.videoPlayerContainer.setVisibility(View.GONE);
        }

        if (callback != null) {
            callback.onZoomStarted(messageObject);
        }
        enterProgress = 0f;
    }

    private void setFullImage(MessageObject messageObject) {
        if (messageObject == null) {
            return;
        }
        if (!messageObject.isPhoto()) {
            return;
        }
        int[] size = new int[1];
        ImageLocation imageLocation = getImageLocation(messageObject, size);
        if (imageLocation != null) {
            boolean cacheOnly = messageObject != null && messageObject.isWebpage();
            Object parentObject;
            parentObject = messageObject;

            String filter = null;
            fullImage.setImage(imageLocation, filter, null, null, null, size[0], null, parentObject, cacheOnly ? 1 : 0);
            fullImage.setCrossfadeAlpha((byte) 2);
        }

        updateViewsLocation();
    }

    private boolean updateViewsLocation() {
        float parentOffsetX = 0;
        float parentOffsetY = 0;
        View currentView = child;
        while (currentView != parentView) {
            if (currentView == null) {
                return false;
            }
            parentOffsetX += currentView.getLeft();
            parentOffsetY += currentView.getTop();
            currentView = (View) currentView.getParent();
        }

        float fragmentOffsetX = 0;
        float fragmentOffsetY = 0;
        currentView = child;
        while (currentView != fragmentView) {
            if (currentView == null) {
                return false;
            }
            fragmentOffsetX += currentView.getLeft();
            fragmentOffsetY += currentView.getTop();
            currentView = (View) currentView.getParent();
        }

        this.fragmentOffsetX = fragmentOffsetX;
        this.fragmentOffsetY = fragmentOffsetY;

        this.parentOffsetX = parentOffsetX;
        this.parentOffsetY = parentOffsetY;
        return true;
    }

    public void finishZoom() {
        if (finishTransition != null || !inOverlayMode) {
            return;
        }
        if (!updateViewsLocation()) {
            clear();
        }
        finishTransition = ValueAnimator.ofFloat(1f, 0);
        finishTransition.addUpdateListener(valueAnimator -> {
            finishProgress = (float) valueAnimator.getAnimatedValue();
            invalidateViews();
        });
        finishTransition.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (finishTransition != null) {
                    finishTransition = null;
                    clear();
                }
            }
        });

        finishTransition.setDuration(220);
        finishTransition.setInterpolator(CubicBezierInterpolator.DEFAULT);
        finishTransition.start();
    }

    public void clear() {
        if (inOverlayMode) {
            if (callback != null) {
                callback.onZoomFinished(messageObject);
            }
            inOverlayMode = false;
        }
        if (overlayView != null && overlayView.getParent() != null) {
            parentView.removeView(overlayView);
            overlayView.backupImageView.getImageReceiver().clearImage();

            if (childImage != null) {
                Drawable drawable = this.childImage.getDrawable();
                if (drawable instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) drawable).removeSecondParentView(overlayView);
                }
            }
        }
        if (child != null) {
            child.invalidate();
            child = null;
        }
        if (childImage != null) {
            this.childImage.onDetachedFromWindow();
            this.childImage.clearImage();
            this.childImage = null;
        }
        if (fullImage != null) {
            fullImage.onDetachedFromWindow();
            fullImage.clearImage();
            fullImage = null;
        }
        if (blurImage != null) {
            blurImage.onDetachedFromWindow();
            blurImage.clearImage();
            blurImage = null;
        }

        messageObject = null;
    }

    public boolean inOverlayMode() {
        return inOverlayMode;
    }


    public boolean isInOverlayMode() {
        return inOverlayMode;
    }

    public boolean isInOverlayModeFor(View child) {
        return inOverlayMode && child == this.child;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (updateViewsLocation() && child != null) {
            ev.offsetLocation(-fragmentOffsetX, -fragmentOffsetY);
            return child.onTouchEvent(ev);
        }
        return false;
    }

    public Bitmap getVideoBitmap(int w, int h) {
        if (overlayView == null) {
            return null;
        }
        return overlayView.videoTextureView.getBitmap(w, h);
    }

    public ImageReceiver getPhotoImage() {
        return childImage;
    }

    protected boolean zoomEnabled(View child, ImageReceiver receiver) {
        Drawable drawable = receiver.getDrawable();
        if (drawable instanceof AnimatedFileDrawable) {
            if (((AnimatedFileDrawable)receiver.getDrawable()).isLoadingStream()) {
                return false;
            } else {
                return true;
            }
        }
        return receiver.hasNotThumb();
    }


    private class ZoomOverlayView extends FrameLayout {

        private FrameLayout videoPlayerContainer;
        private TextureView videoTextureView;
        private AspectRatioFrameLayout aspectRatioFrameLayout;
        private BackupImageView backupImageView;
        private Path aspectPath = new Path();
        private Paint aspectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ZoomOverlayView(Context context) {
            super(context);

            if (Build.VERSION.SDK_INT >= 21) {
                videoPlayerContainer = new FrameLayout(context);
                videoPlayerContainer.setOutlineProvider(new ViewOutlineProvider() {

                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        ImageReceiver imageReceiver = (ImageReceiver) view.getTag(R.id.parent_tag);
                        if (imageReceiver != null) {
                            int[] rad = imageReceiver.getRoundRadius();
                            int maxRad = 0;
                            for (int a = 0; a < 4; a++) {
                                maxRad = Math.max(maxRad, rad[a]);
                            }
                            outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), maxRad);
                        } else {
                            outline.setOval(0, 0, AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize);
                        }
                    }
                });
                videoPlayerContainer.setClipToOutline(true);
            } else {
                videoPlayerContainer = new FrameLayout(context) {

                    RectF rect = new RectF();

                    @Override
                    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                        super.onSizeChanged(w, h, oldw, oldh);
                        aspectPath.reset();
                        ImageReceiver imageReceiver = (ImageReceiver) getTag(R.id.parent_tag);
                        if (imageReceiver != null) {
                            int[] rad = imageReceiver.getRoundRadius();
                            int maxRad = 0;
                            for (int a = 0; a < 4; a++) {
                                maxRad = Math.max(maxRad, rad[a]);
                            }
                            rect.set(0, 0, w, h);
                            aspectPath.addRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Path.Direction.CW);
                        } else {
                            aspectPath.addCircle(w / 2, h / 2, w / 2, Path.Direction.CW);
                        }
                        aspectPath.toggleInverseFillType();
                    }

                    @Override
                    public void setVisibility(int visibility) {
                        super.setVisibility(visibility);
                        if (visibility == VISIBLE) {
                            setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                    }

                    @Override
                    protected void dispatchDraw(Canvas canvas) {
                        super.dispatchDraw(canvas);
                        if (getTag() == null) {
                            canvas.drawPath(aspectPath, aspectPaint);
                        }
                    }
                };
                aspectPath = new Path();
                aspectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                aspectPaint.setColor(0xff000000);
                aspectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            }

            backupImageView = new BackupImageView(context);
            videoPlayerContainer.addView(backupImageView);

            videoPlayerContainer.setWillNotDraw(false);

            aspectRatioFrameLayout = new AspectRatioFrameLayout(context);
            aspectRatioFrameLayout.setBackgroundColor(0);
            videoPlayerContainer.addView(aspectRatioFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            videoTextureView = new TextureView(context);
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            addView(videoPlayerContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            setWillNotDraw(false);
        //    videoTextureView.setVisibility(GONE);
        }


        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (finishTransition == null && enterProgress != 1f) {
                enterProgress += 16f / 220;
                if (enterProgress > 1f) {
                    enterProgress = 1f;
                } else {
                    invalidateViews();
                }
            }

            float progress = finishProgress * CubicBezierInterpolator.DEFAULT.getInterpolation(enterProgress);
            float clipTop = 0;
            float clipBottom = getMeasuredHeight();
            if (progress != 1f && clipBoundsListener != null) {
                clipBoundsListener.getClipTopBottom(clipTopBottom);
                canvas.save();
                clipTop = clipTopBottom[0] * (1f - progress);
                clipBottom = clipTopBottom[1] * (1f - progress) + getMeasuredHeight() * progress;
                canvas.clipRect(0, clipTop, getMeasuredWidth(), clipBottom);
                drawImage(canvas);
                super.dispatchDraw(canvas);
                canvas.restore();
            } else {
                drawImage(canvas);
                super.dispatchDraw(canvas);
            }

            float parentOffsetX = PinchToZoomHelper.this.parentOffsetX - getLeft();
            float parentOffsetY = PinchToZoomHelper.this.parentOffsetY - getTop();

            drawOverlays(canvas, (1f - progress), parentOffsetX, parentOffsetY, clipTop, clipBottom);
        }

        private void drawImage(Canvas canvas) {
            if (!inOverlayMode || child == null || parentView == null) {
                return;
            }

            updateViewsLocation();

            float parentOffsetX = PinchToZoomHelper.this.parentOffsetX - getLeft();
            float parentOffsetY = PinchToZoomHelper.this.parentOffsetY - getTop();

            canvas.save();
            float s = pinchScale * finishProgress + 1f * 1f - finishProgress;
            canvas.scale(s, s, parentOffsetX + pinchCenterX, parentOffsetY + pinchCenterY);
            canvas.translate(parentOffsetX + pinchTranslationX * finishProgress, parentOffsetY + pinchTranslationY * finishProgress);
            if (fullImage != null && fullImage.hasNotThumb()) {
                if (progressToFullView != 1) {
                    progressToFullView += 16f / 150f;
                    if (progressToFullView > 1) {
                        progressToFullView = 1f;
                    } else {
                        invalidateViews();
                    }
                }
                fullImage.setAlpha(progressToFullView);
            }

            float x = imageX;
            float y = imageY;
            if (imageHeight != fullImageHeight || imageWidth != fullImageWidth) {
                float p;
                if (s < 1f) {
                    p = 0;
                } else if (s < 1.4f) {
                    p = (s - 1f) / 0.4f;
                } else  {
                    p = 1f;
                }
                float verticalPadding = (fullImageHeight - imageHeight) / 2f;
                float horizontalPadding = (fullImageWidth - imageWidth) / 2f;
                x = imageX - horizontalPadding * p;
                y = imageY - verticalPadding * p;
                if (childImage != null) {
                    childImage.setImageCoords(x, y, imageWidth + horizontalPadding * p * 2, imageHeight + verticalPadding * p * 2);
                }
            }

            if (!isHardwareVideo) {
                if (childImage != null) {
                    if (progressToFullView != 1f) {
                        childImage.draw(canvas);
                        fullImage.setImageCoords(childImage.getImageX(), childImage.getImageY(), childImage.getImageWidth(), childImage.getImageHeight());
                        fullImage.draw(canvas);
                    } else {
                        fullImage.setImageCoords(childImage.getImageX(), childImage.getImageY(), childImage.getImageWidth(), childImage.getImageHeight());
                        fullImage.draw(canvas);
                    }
                }
            } else {
                videoPlayerContainer.setPivotX(pinchCenterX - imageX);
                videoPlayerContainer.setPivotY(pinchCenterY - imageY);

                videoPlayerContainer.setScaleY(s);
                videoPlayerContainer.setScaleX(s);

                videoPlayerContainer.setTranslationX(x + parentOffsetX + pinchTranslationX * s * finishProgress);
                videoPlayerContainer.setTranslationY(y + parentOffsetY + pinchTranslationY* s * finishProgress);
            }

            if (hasMediaSpoiler) {
                blurImage.setAlpha(childImage.getAlpha());
                blurImage.setRoundRadius(childImage.getRoundRadius());
                blurImage.setImageCoords(childImage.getImageX(), childImage.getImageY(), childImage.getImageWidth(), childImage.getImageHeight());
                blurImage.draw(canvas);

                int[] rad = childImage.getRoundRadius();
                spoilerRadii[0] = spoilerRadii[1] = rad[0];
                spoilerRadii[2] = spoilerRadii[3] = rad[1];
                spoilerRadii[4] = spoilerRadii[5] = rad[2];
                spoilerRadii[6] = spoilerRadii[7] = rad[3];

                AndroidUtilities.rectTmp.set(childImage.getImageX(), childImage.getImageY(), childImage.getImageX2(), childImage.getImageY2());
                path.rewind();
                path.addRoundRect(AndroidUtilities.rectTmp, spoilerRadii, Path.Direction.CW);

                canvas.save();
                canvas.clipPath(path);
                int sColor = Color.WHITE;
                mediaSpoilerEffect.setColor(ColorUtils.setAlphaComponent(sColor, (int) (Color.alpha(sColor) * 0.325f * childImage.getAlpha())));
                mediaSpoilerEffect.setBounds((int) childImage.getImageX(), (int) childImage.getImageY(), (int) childImage.getImageX2(), (int) childImage.getImageY2());
                mediaSpoilerEffect.draw(canvas);
                canvas.restore();

                invalidate();
            }

            canvas.restore();
        }
    }

    protected void drawOverlays(Canvas canvas, float alpha, float parentOffsetX, float parentOffsetY, float clipTop, float clipBottom) {

    }

    private ImageLocation getImageLocation(MessageObject message, int[] size) {
        if (message.messageOwner instanceof TLRPC.TL_messageService) {
            if (message.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                return null;
            } else {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    if (size != null) {
                        size[0] = sizeFull.size;
                        if (size[0] == 0) {
                            size[0] = -1;
                        }
                    }
                    return ImageLocation.getForObject(sizeFull, message.photoThumbsObject);
                } else if (size != null) {
                    size[0] = -1;
                }
            }
        } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo != null || message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage != null) {
            if (message.isGif()) {
                return ImageLocation.getForDocument(message.getDocument());
            } else {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize(), false, null, true);
                if (sizeFull != null) {
                    if (size != null) {
                        size[0] = sizeFull.size;
                        if (size[0] == 0) {
                            size[0] = -1;
                        }
                    }
                    return ImageLocation.getForObject(sizeFull, message.photoThumbsObject);
                } else if (size != null) {
                    size[0] = -1;
                }
            }
        } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
            return ImageLocation.getForWebFile(WebFile.createWithWebDocument(((TLRPC.TL_messageMediaInvoice) message.messageOwner.media).webPhoto));
        } else if (message.getDocument() != null) {
            TLRPC.Document document = message.getDocument();
            if (MessageObject.isDocumentHasThumb(message.getDocument())) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                if (size != null) {
                    size[0] = thumb.size;
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                }
                return ImageLocation.getForDocument(thumb, document);
            }
        }
        return null;
    }

    public void setClipBoundsListener(ClipBoundsListener clipBoundsListener) {
        this.clipBoundsListener = clipBoundsListener;
    }

    public interface Callback {
        default TextureView getCurrentTextureView() {
            return null;
        }
        default void onZoomStarted(MessageObject messageObject) {

        }
        default void onZoomFinished(MessageObject messageObject) {

        }

    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface ClipBoundsListener {
        void getClipTopBottom(float[] topBottom);
    }

    public boolean checkPinchToZoom(MotionEvent ev, View child, ImageReceiver image, MessageObject messageObject) {
        if (!zoomEnabled(child, image)) {
            return false;
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (!isInPinchToZoomTouchMode && ev.getPointerCount() == 2) {
                pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                pinchStartCenterX = pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                pinchStartCenterY = pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                pinchScale = 1f;

                pointerId1 = ev.getPointerId(0);
                pointerId2 = ev.getPointerId(1);
                isInPinchToZoomTouchMode = true;
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
            int index1 = -1;
            int index2 = -1;
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (pointerId1 == ev.getPointerId(i)) {
                    index1 = i;
                }
                if (pointerId2 == ev.getPointerId(i)) {
                    index2 = i;
                }
            }
            if (index1 == -1 || index2 == -1) {
                isInPinchToZoomTouchMode = false;
                child.getParent().requestDisallowInterceptTouchEvent(false);
                finishZoom();
                return false;
            }
            pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
            if (pinchScale > 1.005f && !isInOverlayMode()) {
                pinchStartDistance = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1));
                pinchStartCenterX = pinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                pinchStartCenterY = pinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;
                pinchScale = 1f;
                pinchTranslationX = 0f;
                pinchTranslationY = 0f;
                child.getParent().requestDisallowInterceptTouchEvent(true);
                startZoom(child, image, messageObject);

            }

            float newPinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
            float newPinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;



            float moveDx = pinchStartCenterX - newPinchCenterX;
            float moveDy = pinchStartCenterY - newPinchCenterY;
            pinchTranslationX = -moveDx / pinchScale;
            pinchTranslationY = -moveDy / pinchScale;
            invalidateViews();
        } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) && isInPinchToZoomTouchMode) {
            isInPinchToZoomTouchMode = false;
            if (child != null && child.getParent() != null) {
                child.getParent().requestDisallowInterceptTouchEvent(false);
            }
            finishZoom();
        }
        return isInOverlayModeFor(child);
    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    protected void invalidateViews() {
        if (overlayView != null) {
            overlayView.invalidate();
        }
    }

    public View getChild() {
        return child;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stars.StarsIntroActivity;

public class PhotoAttachPhotoCell extends FrameLayout {

    private BackupImageView imageView;
    private FrameLayout container;
    private FrameLayout checkFrame;
    private CheckBox2 checkBox;
    private TextView videoTextView;
    private FrameLayout videoInfoContainer;
    private AnimatorSet animatorSet;
    private boolean isLast;
    private boolean pressed;
    private static Rect rect = new Rect();
    private PhotoAttachPhotoCellDelegate delegate;
    private boolean itemSizeChanged;
    private boolean needCheckShow;
    private int itemSize;
    private boolean isVertical;
    private boolean zoomOnSelect = true;

    private MediaController.PhotoEntry photoEntry;
    private MediaController.SearchImage searchEntry;

    private Paint backgroundPaint = new Paint();
    private AnimatorSet animator;
    private final Theme.ResourcesProvider resourcesProvider;

    private SpoilerEffect spoilerEffect = new SpoilerEffect();
    private SpoilerEffect2 spoilerEffect2;
    private boolean hasSpoiler;

    private long stars;
    private boolean starsSelectedMultiple;

    private Path path = new Path();
    private float spoilerRevealX;
    private float spoilerRevealY;
    private float spoilerMaxRadius;
    private float spoilerRevealProgress;

    private Bitmap imageViewCrossfadeSnapshot;
    private Float crossfadeDuration;
    private float imageViewCrossfadeProgress = 1f;

    public interface PhotoAttachPhotoCellDelegate {
        void onCheckClick(PhotoAttachPhotoCell v);
    }

    public PhotoAttachPhotoCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);

        container = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (spoilerEffect2 != null && child == imageView) {
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    if (hasSpoiler && spoilerRevealProgress != 1f && (photoEntry == null || !photoEntry.isAttachSpoilerRevealed)) {
                        if (spoilerRevealProgress != 0f) {
                            canvas.save();
                            path.rewind();
                            path.addCircle(spoilerRevealX, spoilerRevealY, spoilerMaxRadius * spoilerRevealProgress, Path.Direction.CW);
                            canvas.clipPath(path, Region.Op.DIFFERENCE);
                        }
//                        float alphaProgress = CubicBezierInterpolator.DEFAULT.getInterpolation(1f - imageViewCrossfadeProgress);
//                        float alpha = hasSpoiler ? alphaProgress : 1f - alphaProgress;
                        spoilerEffect2.draw(canvas, container, imageView.getMeasuredWidth(), imageView.getMeasuredHeight());
                        if (photoEntry != null && photoEntry.starsAmount > 0) {
                            imageView.drawBlurredText(canvas, 1f);
                        }
                        if (spoilerRevealProgress != 0f) {
                            canvas.restore();
                        }
                    }
                    return r;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        addView(container, LayoutHelper.createFrame(80, 80));

        int sColor = Color.WHITE;
        spoilerEffect.setColor(ColorUtils.setAlphaComponent(sColor, (int) (Color.alpha(sColor) * 0.325f)));
        imageView = new BackupImageView(context) {
            private Paint crossfadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private long lastUpdate;

            @Override
            protected void onDraw(Canvas canvas) {
                ImageReceiver imageReceiver = animatedEmojiDrawable != null ? animatedEmojiDrawable.getImageReceiver() : this.imageReceiver;
                if (imageReceiver == null) {
                    return;
                }
                if (width != -1 && height != -1) {
                    imageReceiver.setImageCoords((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
                    blurImageReceiver.setImageCoords((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
                } else {
                    imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
                    blurImageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
                }
                imageReceiver.draw(canvas);

                if (hasSpoiler && spoilerRevealProgress != 1f && (photoEntry == null || !photoEntry.isAttachSpoilerRevealed)) {
                    if (spoilerRevealProgress != 0f) {
                        canvas.save();
                        path.rewind();
                        path.addCircle(spoilerRevealX, spoilerRevealY, spoilerMaxRadius * spoilerRevealProgress, Path.Direction.CW);
                        canvas.clipPath(path, Region.Op.DIFFERENCE);
                    }

                    blurImageReceiver.draw(canvas);
                    if (spoilerEffect2 == null) {
                        spoilerEffect.setBounds(0, 0, getWidth(), getHeight());
                        spoilerEffect.draw(canvas);
                    }
                    invalidate();

                    if (spoilerRevealProgress != 0f) {
                        canvas.restore();
                    }
                }

                if (imageViewCrossfadeProgress != 1f && imageViewCrossfadeSnapshot != null) {
                    crossfadePaint.setAlpha((int) (CubicBezierInterpolator.DEFAULT.getInterpolation(1f - imageViewCrossfadeProgress) * 0xFF));
                    canvas.drawBitmap(imageViewCrossfadeSnapshot, 0, 0, crossfadePaint);
                    long dt = Math.min(16, System.currentTimeMillis() - lastUpdate);
                    float duration = crossfadeDuration == null ? 250f : crossfadeDuration;
                    imageViewCrossfadeProgress = Math.min(1f, imageViewCrossfadeProgress + dt / duration);
                    lastUpdate = System.currentTimeMillis();
                    invalidate();
                    if (spoilerEffect2 != null) {
                        container.invalidate();
                    }
                } else if (imageViewCrossfadeProgress == 1f && imageViewCrossfadeSnapshot != null) {
                    imageViewCrossfadeSnapshot.recycle();
                    imageViewCrossfadeSnapshot = null;
                    crossfadeDuration = null;
                    invalidate();
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                updateSpoilers2(photoEntry != null && photoEntry.hasSpoiler);
            }
        };
        imageView.setBlurAllowed(true);
        container.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        videoInfoContainer = new FrameLayout(context) {

            private RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);
            }
        };
        videoInfoContainer.setWillNotDraw(false);
        videoInfoContainer.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);
        container.addView(videoInfoContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 17, Gravity.BOTTOM | Gravity.LEFT, 4, 0, 0, 4));

        ImageView imageView1 = new ImageView(context);
        imageView1.setImageResource(R.drawable.play_mini_video);
        videoInfoContainer.addView(imageView1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        videoTextView = new TextView(context);
        videoTextView.setTextColor(0xffffffff);
        videoTextView.setTypeface(AndroidUtilities.bold());
        videoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        videoTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        videoInfoContainer.addView(videoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 13, -0.7f, 0, 0));

        checkBox = new CheckBox2(context, 24, resourcesProvider);
        checkBox.setDrawBackgroundAsArc(7);
        checkBox.setColor(Theme.key_chat_attachCheckBoxBackground, Theme.key_chat_attachPhotoBackground, Theme.key_chat_attachCheckBoxCheck);
        addView(checkBox, LayoutHelper.createFrame(26, 26, Gravity.LEFT | Gravity.TOP, 52, 4, 0, 0));
        checkBox.setVisibility(VISIBLE);
        setFocusable(true);

        checkFrame = new FrameLayout(context);
        addView(checkFrame, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.TOP, 38, 0, 0, 0));

        itemSize = AndroidUtilities.dp(80);
    }

    public boolean canRevealSpoiler() {
        return hasSpoiler && spoilerRevealProgress == 0f && (photoEntry == null || !photoEntry.isAttachSpoilerRevealed);
    }

    public void startRevealMedia(float x, float y) {
        spoilerRevealX = x;
        spoilerRevealY = y;

        spoilerMaxRadius = (float) Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2));
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration((long) MathUtils.clamp(spoilerMaxRadius * 0.3f, 250, 550));
        animator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        animator.addUpdateListener(animation -> {
            spoilerRevealProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                photoEntry.isAttachSpoilerRevealed = true;
                invalidate();
            }
        });
        animator.start();
    }

    public void setHasSpoiler(boolean hasSpoiler) {
        setHasSpoiler(hasSpoiler, null);
    }

    public void setHasSpoiler(boolean hasSpoiler, Float crossfadeDuration) {
        if (this.hasSpoiler != hasSpoiler) {
            spoilerRevealProgress = 0f;
            if (isLaidOut()) {
                Bitmap prevSnapshot = imageViewCrossfadeSnapshot;
                imageViewCrossfadeSnapshot = AndroidUtilities.snapshotView(imageView);
                if (prevSnapshot != null) {
                    prevSnapshot.recycle();
                }
                imageViewCrossfadeProgress = 0f;
            } else {
                if (imageViewCrossfadeSnapshot != null) {
                    imageViewCrossfadeSnapshot.recycle();
                    imageViewCrossfadeSnapshot = null;
                }
                imageViewCrossfadeProgress = 1f;
            }

            this.hasSpoiler = hasSpoiler;
            this.crossfadeDuration = crossfadeDuration;
            imageView.setHasBlur(hasSpoiler);
            imageView.invalidate();
            if (hasSpoiler) {
                updateSpoilers2(hasSpoiler);
            }
        }
    }

    private SpannableString star, lock;
    public void setStarsPrice(long stars, boolean multiple) {
        if (multiple != starsSelectedMultiple || stars != this.stars) {
            this.stars = stars;
            this.starsSelectedMultiple = multiple;

            SpannableStringBuilder s = null;
            if (stars > 0) {
                s = new SpannableStringBuilder();
                if (star == null) {
                    star = new SpannableString("⭐");
                    ColoredImageSpan span = new ColoredImageSpan(R.drawable.star_small_inner);
                    span.setScale(.7f, .7f);
                    star.setSpan(span, 0, star.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                s.append(star);
                s.append(" ");
                if (multiple) {
                    if (lock == null) {
                        lock = new SpannableString("l");
                        ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_mini_lock2);
                        lock.setSpan(span, 0, lock.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    s.append(lock);
                } else {
                    s.append(Long.toString(stars));
                }
            }
            imageView.setBlurredText(s);
            imageView.invalidate();
            container.invalidate();
        }
    }

    private void updateSpoilers2(boolean hasSpoiler) {
        if (container == null || imageView == null || imageView.getMeasuredHeight() <= 0 || imageView.getMeasuredWidth() <= 0) {
            return;
        }
        if (hasSpoiler && SpoilerEffect2.supports()) {
            if (spoilerEffect2 == null) {
                spoilerEffect2 = SpoilerEffect2.getInstance(container);
            }
        } else {
            if (spoilerEffect2 != null) {
                spoilerEffect2.detach(this);
                spoilerEffect2 = null;
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (spoilerEffect2 != null) {
            spoilerEffect2.detach(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (spoilerEffect2 != null) {
            if (spoilerEffect2.destroyed) {
                spoilerEffect2 = SpoilerEffect2.getInstance(this);
            } else {
                spoilerEffect2.attach(this);
            }
        }
    }

    public void setIsVertical(boolean value) {
        isVertical = value;
    }

    public void setItemSize(int size) {
        itemSize = size;

        LayoutParams layoutParams = (LayoutParams) container.getLayoutParams();
        layoutParams.width = layoutParams.height = itemSize;

        layoutParams = (LayoutParams) checkFrame.getLayoutParams();
        layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        layoutParams.leftMargin = 0;

        layoutParams = (LayoutParams) checkBox.getLayoutParams();
        layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        layoutParams.leftMargin = 0;
        layoutParams.rightMargin = layoutParams.topMargin = AndroidUtilities.dp(5);
        checkBox.setDrawBackgroundAsArc(6);

        itemSizeChanged = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (itemSizeChanged) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(5), MeasureSpec.EXACTLY));
        } else {
            if (isVertical) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80 + (isLast ? 0 : 6)), MeasureSpec.EXACTLY));
            } else {
                super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80 + (isLast ? 0 : 6)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
            }
        }
    }

    public MediaController.PhotoEntry getPhotoEntry() {
        return photoEntry;
    }

    public BackupImageView getImageView() {
        return imageView;
    }

    public float getScale() {
        return container.getScaleX();
    }

    public CheckBox2 getCheckBox() {
        return checkBox;
    }

    public FrameLayout getCheckFrame() {
        return checkFrame;
    }

    public View getVideoInfoContainer() {
        return videoInfoContainer;
    }

    public void setPhotoEntry(MediaController.PhotoEntry entry, boolean selectedMultiple, boolean needCheckShow, boolean last) {
        pressed = false;
        photoEntry = entry;
        isLast = last;
        if (photoEntry.isVideo) {
            imageView.setOrientation(0, true);
            videoInfoContainer.setVisibility(VISIBLE);
            videoTextView.setText(AndroidUtilities.formatShortDuration(photoEntry.duration));
        } else {
            videoInfoContainer.setVisibility(INVISIBLE);
        }
        if (photoEntry.coverPath != null) {
            imageView.setImage(photoEntry.coverPath, null, Theme.chat_attachEmptyDrawable);
        } else if (photoEntry.thumbPath != null) {
            imageView.setImage(photoEntry.thumbPath, null, Theme.chat_attachEmptyDrawable);
        } else if (photoEntry.path != null) {
            if (photoEntry.isVideo) {
                imageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
            } else {
                imageView.setOrientation(photoEntry.orientation, photoEntry.invert, true);
                imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
            }
        } else {
            imageView.setImageDrawable(Theme.chat_attachEmptyDrawable);
        }
        boolean showing = needCheckShow && PhotoViewer.isShowingImage(photoEntry.path);
        imageView.getImageReceiver().setVisible(!showing, true);
        checkBox.setAlpha(showing ? 0.0f : 1.0f);
        videoInfoContainer.setAlpha(showing ? 0.0f : 1.0f);
        requestLayout();
        setHasSpoiler(entry.hasSpoiler);
        setStarsPrice(entry.starsAmount, selectedMultiple);
    }

    public void setPhotoEntry(MediaController.SearchImage searchImage, boolean needCheckShow, boolean last) {
        pressed = false;
        searchEntry = searchImage;
        isLast = last;

        Drawable thumb = zoomOnSelect ? Theme.chat_attachEmptyDrawable : getResources().getDrawable(R.drawable.nophotos);
        if (searchImage.thumbPhotoSize != null) {
            imageView.setImage(ImageLocation.getForPhoto(searchImage.thumbPhotoSize, searchImage.photo), null, thumb, searchImage);
        } else if (searchImage.photoSize != null) {
            imageView.setImage(ImageLocation.getForPhoto(searchImage.photoSize, searchImage.photo), "80_80", thumb, searchImage);
        } else if (searchImage.thumbPath != null) {
            imageView.setImage(searchImage.thumbPath, null, thumb);
        } else if (!TextUtils.isEmpty(searchImage.thumbUrl)) {
            ImageLocation location = ImageLocation.getForPath(searchImage.thumbUrl);
            if (searchImage.type == 1 && searchImage.thumbUrl.endsWith("mp4")) {
                location.imageType = FileLoader.IMAGE_TYPE_ANIMATION;
            }
            imageView.setImage(location, null, thumb, searchImage);
        } else if (searchImage.document != null) {
            MessageObject.getDocumentVideoThumb(searchImage.document);
            TLRPC.VideoSize videoSize = MessageObject.getDocumentVideoThumb(searchImage.document);
            if (videoSize != null) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(searchImage.document.thumbs, 90);
                imageView.setImage(ImageLocation.getForDocument(videoSize, searchImage.document), null, ImageLocation.getForDocument(currentPhotoObject, searchImage.document), "52_52", null, -1, 1, searchImage);
            } else {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(searchImage.document.thumbs, 320);
                imageView.setImage(ImageLocation.getForDocument(photoSize, searchImage.document), null, thumb, searchImage);
            }
        } else {
            imageView.setImageDrawable(thumb);
        }
        boolean showing = needCheckShow && PhotoViewer.isShowingImage(searchImage.getPathToAttach());
        imageView.getImageReceiver().setVisible(!showing, true);
        checkBox.setAlpha(showing ? 0.0f : 1.0f);
        videoInfoContainer.setAlpha(showing ? 0.0f : 1.0f);
        requestLayout();
        setHasSpoiler(false);
        setStarsPrice(0, false);
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public void setChecked(int num, boolean checked, boolean animated) {
        checkBox.setChecked(num, checked, animated);
        if (itemSizeChanged) {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (animated) {
                animator = new AnimatorSet();
                animator.playTogether(
                        ObjectAnimator.ofFloat(container, View.SCALE_X, checked ? 0.787f : 1.0f),
                        ObjectAnimator.ofFloat(container, View.SCALE_Y, checked ? 0.787f : 1.0f));
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animator != null && animator.equals(animation)) {
                            animator = null;
                            if (!checked) {
                                setBackgroundColor(0);
                            }
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animator != null && animator.equals(animation)) {
                            animator = null;
                        }
                    }
                });
                animator.start();
            } else {
                container.setScaleX(checked ? 0.787f : 1.0f);
                container.setScaleY(checked ? 0.787f : 1.0f);
            }
        }
    }

    public void setNum(int num) {
        checkBox.setNum(num);
    }

    public void setOnCheckClickListener(OnClickListener onCheckClickListener) {
        checkFrame.setOnClickListener(onCheckClickListener);
    }

    public void setDelegate(PhotoAttachPhotoCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void callDelegate() {
        delegate.onCheckClick(this);
    }

    public void showImage() {
        imageView.getImageReceiver().setVisible(true, true);
    }

    public void showCheck(boolean show) {
        if (show && checkBox.getAlpha() == 1 || !show && checkBox.getAlpha() == 0) {
            return;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(videoInfoContainer, View.ALPHA, show ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(checkBox, View.ALPHA, show ? 1.0f : 0.0f));
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(animatorSet)) {
                    animatorSet = null;
                }
            }
        });
        animatorSet.start();
    }

    @Override
    public void clearAnimation() {
        super.clearAnimation();
        if (animator != null) {
            animator.cancel();
            animator = null;

            container.setScaleX(checkBox.isChecked() ? 0.787f : 1.0f);
            container.setScaleY(checkBox.isChecked() ? 0.787f : 1.0f);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;

        checkFrame.getHitRect(rect);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (rect.contains((int) event.getX(), (int) event.getY())) {
                pressed = true;
                invalidate();
                result = true;
            }
        } else if (pressed) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(true);
                pressed = false;
                playSoundEffect(SoundEffectConstants.CLICK);
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                delegate.onCheckClick(this);
                invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (!(rect.contains((int) event.getX(), (int) event.getY()))) {
                    pressed = false;
                    invalidate();
                }
            }
        }
        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (checkBox.isChecked() || container.getScaleX() != 1.0f || !imageView.getImageReceiver().hasNotThumb() || imageView.getImageReceiver().getCurrentAlpha() != 1.0f || photoEntry != null && PhotoViewer.isShowingImage(photoEntry.path) || searchEntry != null && PhotoViewer.isShowingImage(searchEntry.getPathToAttach())) {
            backgroundPaint.setColor(getThemedColor(Theme.key_chat_attachPhotoBackground));
            canvas.drawRect(0, 0, imageView.getMeasuredWidth(), imageView.getMeasuredHeight(), backgroundPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(true);
        StringBuilder sb = new StringBuilder();
        if (photoEntry != null && photoEntry.isVideo) {
            sb.append(LocaleController.getString(R.string.AttachVideo) + ", " + LocaleController.formatDuration(photoEntry.duration));
        } else {
            sb.append(LocaleController.getString(R.string.AttachPhoto));
        }
        if (photoEntry != null) {
            sb.append(". ");
            sb.append(LocaleController.getInstance().getFormatterStats().format(photoEntry.dateTaken * 1000L));
        }
        info.setText(sb);
        if (checkBox.isChecked()) {
            info.setSelected(true);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_open_photo, LocaleController.getString(R.string.Open)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.id.acc_action_open_photo) {
            View parent = (View) getParent();
            parent.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, getLeft(), getTop() + getHeight() - 1, 0));
            parent.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, getLeft(), getTop() + getHeight() - 1, 0));
        }
        return super.performAccessibilityAction(action, arguments);
    }

    protected int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}

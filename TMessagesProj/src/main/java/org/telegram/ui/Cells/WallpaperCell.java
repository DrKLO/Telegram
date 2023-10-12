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
import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.WallpapersListActivity;

public class WallpaperCell extends FrameLayout {

    int size;
    public boolean drawStubBackground = true;

    public class WallpaperView extends FrameLayout {

        private BackupImageView imageView;
        private ImageView imageView2;
        private CheckBox checkBox;
        private View selector;
        private boolean isSelected;

        private AnimatorSet animator;
        private AnimatorSet animatorSet;

        private Object currentWallpaper;

        public WallpaperView(Context context) {
            super(context);
            setWillNotDraw(false);

            imageView = new BackupImageView(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper || currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                        canvas.drawLine(1, 0, getMeasuredWidth() - 1, 0, framePaint);
                        canvas.drawLine(0, 0, 0, getMeasuredHeight(), framePaint);
                        canvas.drawLine(getMeasuredWidth() - 1, 0, getMeasuredWidth() - 1, getMeasuredHeight(), framePaint);
                        canvas.drawLine(1, getMeasuredHeight() - 1, getMeasuredWidth() - 1, getMeasuredHeight() - 1, framePaint);
                    }
                    if (isSelected) {
                        circlePaint.setColor(Theme.serviceMessageColorBackup);
                        int cx = getMeasuredWidth() / 2;
                        int cy = getMeasuredHeight() / 2;
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(20), circlePaint);
                        checkDrawable.setBounds(cx - checkDrawable.getIntrinsicWidth() / 2, cy - checkDrawable.getIntrinsicHeight() / 2, cx + checkDrawable.getIntrinsicWidth() / 2, cy + checkDrawable.getIntrinsicHeight() / 2);
                        checkDrawable.draw(canvas);
                    }
                }
            };
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

            imageView2 = new ImageView(context);
            imageView2.setImageResource(R.drawable.ic_gallery_background);
            imageView2.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

            selector = new View(context);
            selector.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            addView(selector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            checkBox = new CheckBox(context, R.drawable.round_check2);
            checkBox.setVisibility(INVISIBLE);
            checkBox.setColor(Theme.getColor(Theme.key_checkbox), Theme.getColor(Theme.key_checkboxCheck));
            addView(checkBox, LayoutHelper.createFrame(22, 22, Gravity.RIGHT | Gravity.TOP, 0, 2, 2, 0));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (Build.VERSION.SDK_INT >= 21) {
                selector.drawableHotspotChanged(event.getX(), event.getY());
            }
            return super.onTouchEvent(event);
        }

        public void setWallpaper(Object object, Object selectedWallpaper, Drawable themedWallpaper, boolean themed) {
            currentWallpaper = object;
            imageView.setVisibility(VISIBLE);
            imageView2.setVisibility(INVISIBLE);
            imageView.setBackgroundDrawable(null);
            imageView.getImageReceiver().setColorFilter(null);
            imageView.getImageReceiver().setAlpha(1.0f);
            imageView.getImageReceiver().setBlendMode(null);
            imageView.getImageReceiver().setGradientBitmap(null);
            isSelected = object == selectedWallpaper;
            int thumbSide = 100, imageSide = 180;
            String imageFilter = imageSide + "_" + imageSide, thumbFilter = thumbSide + "_" + thumbSide + "_b";
            if (object instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, AndroidUtilities.dp(thumbSide));
                TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, AndroidUtilities.dp(imageSide));
                if (image == thumb) {
                    image = null;
                }
                long size = image != null ? image.size : wallPaper.document.size;
                if (wallPaper.pattern) {
                    int patternColor;
                    if (wallPaper.settings.third_background_color != 0) {
                        MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(wallPaper.settings.background_color, wallPaper.settings.second_background_color, wallPaper.settings.third_background_color, wallPaper.settings.fourth_background_color, true);
                        if (wallPaper.settings.intensity >= 0 || !Theme.getActiveTheme().isDark()) {
                            imageView.setBackground(motionBackgroundDrawable);
                            if (Build.VERSION.SDK_INT >= 29) {
                                imageView.getImageReceiver().setBlendMode(BlendMode.SOFT_LIGHT);
                            }
                        } else {
                            imageView.getImageReceiver().setGradientBitmap(motionBackgroundDrawable.getBitmap());
                        }
                        patternColor = MotionBackgroundDrawable.getPatternColor(wallPaper.settings.background_color, wallPaper.settings.second_background_color, wallPaper.settings.third_background_color, wallPaper.settings.fourth_background_color);
                    } else {
                        imageView.setBackgroundColor(Theme.getWallpaperColor(wallPaper.settings.background_color));
                        patternColor = AndroidUtilities.getPatternColor(wallPaper.settings.background_color);
                    }
                    if (Build.VERSION.SDK_INT < 29 || wallPaper.settings.third_background_color == 0) {
                        imageView.getImageReceiver().setColorFilter(new PorterDuffColorFilter(AndroidUtilities.getPatternColor(patternColor), PorterDuff.Mode.SRC_IN));
                    }
                    if (image != null) {
                        imageView.setImage(ImageLocation.getForDocument(image, wallPaper.document), imageFilter, ImageLocation.getForDocument(thumb, wallPaper.document), null, "jpg", size, 1, wallPaper);
                    } else {
                        imageView.setImage(ImageLocation.getForDocument(thumb, wallPaper.document), imageFilter, null, null, "jpg", size, 1, wallPaper);
                    }
                    imageView.getImageReceiver().setAlpha(Math.abs(wallPaper.settings.intensity) / 100.0f);
                } else {
                    if (image != null) {
                        imageView.setImage(ImageLocation.getForDocument(image, wallPaper.document), imageFilter, ImageLocation.getForDocument(thumb, wallPaper.document), thumbFilter, "jpg", size, 1, wallPaper);
                    } else {
                        imageView.setImage(ImageLocation.getForDocument(wallPaper.document), imageFilter, ImageLocation.getForDocument(thumb, wallPaper.document), thumbFilter, "jpg", size, 1, wallPaper);
                    }
                }
            } else if (object instanceof WallpapersListActivity.ColorWallpaper) {
                WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) object;
                if (wallPaper.path != null || wallPaper.pattern != null || Theme.DEFAULT_BACKGROUND_SLUG.equals(wallPaper.slug)) {
                    int patternColor;
                    if (wallPaper.gradientColor2 != 0) {
                        MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(wallPaper.color, wallPaper.gradientColor1, wallPaper.gradientColor2, wallPaper.gradientColor3, true);
                        if (wallPaper.intensity >= 0) {
                            imageView.setBackground(new MotionBackgroundDrawable(wallPaper.color, wallPaper.gradientColor1, wallPaper.gradientColor2, wallPaper.gradientColor3, true));
                            if (Build.VERSION.SDK_INT >= 29) {
                                imageView.getImageReceiver().setBlendMode(BlendMode.SOFT_LIGHT);
                            }
                        } else {
                            imageView.getImageReceiver().setGradientBitmap(motionBackgroundDrawable.getBitmap());
                        }
                        patternColor = MotionBackgroundDrawable.getPatternColor(wallPaper.color, wallPaper.gradientColor1, wallPaper.gradientColor2, wallPaper.gradientColor3);
                    } else {
                        patternColor = AndroidUtilities.getPatternColor(wallPaper.color);
                    }
                    if (Theme.DEFAULT_BACKGROUND_SLUG.equals(wallPaper.slug)) {
                        if (wallPaper.defaultCache == null) {
                            wallPaper.defaultCache = SvgHelper.getBitmap(R.raw.default_pattern, 100, 180, Color.BLACK);
                        }
                        imageView.setImageBitmap(wallPaper.defaultCache);
                        imageView.getImageReceiver().setAlpha(Math.abs(wallPaper.intensity));
                    } else if (wallPaper.path != null) {
                        imageView.setImage(wallPaper.path.getAbsolutePath(), imageFilter, null);
                    } else {
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.pattern.document.thumbs, 100);
                        long size = thumb != null ? thumb.size : wallPaper.pattern.document.size;
                        imageView.setImage(ImageLocation.getForDocument(thumb, wallPaper.pattern.document), imageFilter, null, null, "jpg", size, 1, wallPaper.pattern);
                        imageView.getImageReceiver().setAlpha(Math.abs(wallPaper.intensity));
                        if (Build.VERSION.SDK_INT < 29 || wallPaper.gradientColor2 == 0) {
                            imageView.getImageReceiver().setColorFilter(new PorterDuffColorFilter(AndroidUtilities.getPatternColor(patternColor), PorterDuff.Mode.SRC_IN));
                        }
                    }
                } else {
                    imageView.setImageBitmap(null);
                    if (wallPaper.isGradient) {
                        imageView.setBackground(new MotionBackgroundDrawable(wallPaper.color, wallPaper.gradientColor1, wallPaper.gradientColor2, wallPaper.gradientColor3, true));
                    } else if (wallPaper.gradientColor1 != 0) {
                        imageView.setBackground(new GradientDrawable(GradientDrawable.Orientation.BL_TR, new int[]{0xff000000 | wallPaper.color, 0xff000000 | wallPaper.gradientColor1}));
                    } else {
                        imageView.setBackgroundColor(0xff000000 | wallPaper.color);
                    }
                }
            } else if (object instanceof WallpapersListActivity.FileWallpaper) {
                WallpapersListActivity.FileWallpaper wallPaper = (WallpapersListActivity.FileWallpaper) object;
                if (wallPaper.originalPath != null) {
                    imageView.setImage(wallPaper.originalPath.getAbsolutePath(), imageFilter, null);
                } else if (wallPaper.path != null) {
                    imageView.setImage(wallPaper.path.getAbsolutePath(), imageFilter, null);
                } else if (Theme.THEME_BACKGROUND_SLUG.equals(wallPaper.slug)) {
                    imageView.setImageDrawable(Theme.getThemedWallpaper(true, imageView));
                } else {
                    imageView.setImageResource(wallPaper.thumbResId);
                }
            } else if (object instanceof MediaController.SearchImage) {
                MediaController.SearchImage wallPaper = (MediaController.SearchImage) object;
                if (wallPaper.photo != null) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, AndroidUtilities.dp(thumbSide));
                    TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, AndroidUtilities.dp(imageSide));
                    if (image == thumb) {
                        image = null;
                    }
                    int size = image != null ? image.size : 0;
                    imageView.setImage(ImageLocation.getForPhoto(image, wallPaper.photo), imageFilter, ImageLocation.getForPhoto(thumb, wallPaper.photo), thumbFilter, "jpg", size, 1, wallPaper);
                } else {
                    imageView.setImage(wallPaper.thumbUrl, imageFilter, null);
                }
            } else {
                isSelected = false;
            }
        }

        public void setChecked(final boolean checked, boolean animated) {
            if (checkBox.getVisibility() != VISIBLE) {
                checkBox.setVisibility(VISIBLE);
            }
            checkBox.setChecked(checked, animated);
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (animated) {
                animator = new AnimatorSet();
                animator.playTogether(ObjectAnimator.ofFloat(imageView, "scaleX", checked ? 0.8875f : 1.0f),
                        ObjectAnimator.ofFloat(imageView, "scaleY", checked ? 0.8875f : 1.0f));
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
                imageView.setScaleX(checked ? 0.8875f : 1.0f);
                imageView.setScaleY(checked ? 0.8875f : 1.0f);
            }
            invalidate();
        }

        @Override
        public void invalidate() {
            super.invalidate();
            imageView.invalidate();
        }

        @Override
        public void clearAnimation() {
            super.clearAnimation();
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawStubBackground && checkBox.isChecked() || !imageView.getImageReceiver().hasBitmapImage() || imageView.getImageReceiver().getCurrentAlpha() != 1.0f) {
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }
        }
    }

    private WallpaperView[] wallpaperViews;
    private int spanCount = 3;
    private boolean isTop;
    private boolean isBottom;
    private int currentType;
    private Paint framePaint;
    private Paint circlePaint;
    private Paint backgroundPaint;
    private Drawable checkDrawable;

    public WallpaperCell(Context context) {
        this(context, 5);
    }

    public WallpaperCell(Context context, int size) {
        super(context);

        wallpaperViews = new WallpaperView[size];
        for (int a = 0; a < wallpaperViews.length; a++) {
            WallpaperView wallpaperView = wallpaperViews[a] = new WallpaperView(context);
            int num = a;
            addView(wallpaperView);
            wallpaperView.setOnClickListener(v -> onWallpaperClick(wallpaperView.currentWallpaper, num));
            wallpaperView.setOnLongClickListener(v -> onWallpaperLongClick(wallpaperView.currentWallpaper, num));
        }

        framePaint = new Paint();
        framePaint.setColor(0x33000000);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        checkDrawable = context.getResources().getDrawable(R.drawable.background_selected).mutate();

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Theme.getColor(Theme.key_sharedMedia_photoPlaceholder));
    }

    protected void onWallpaperClick(Object wallPaper, int index) {

    }

    protected boolean onWallpaperLongClick(Object wallPaper, int index) {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (spanCount == 1) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size + AndroidUtilities.dp(6), MeasureSpec.EXACTLY));
            setPadding(0, 0, 0, AndroidUtilities.dp(6));
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int availableWidth = width - AndroidUtilities.dp(14 * 2 + 6 * (spanCount - 1));
        int itemWidth = availableWidth / spanCount;
        int height = currentType == WallpapersListActivity.TYPE_ALL ? AndroidUtilities.dp(180) : itemWidth;
        setMeasuredDimension(width, height + (isTop ? AndroidUtilities.dp(14) : 0) + (AndroidUtilities.dp(isBottom ? 14 : 6)));

        for (int a = 0; a < spanCount; a++) {
            wallpaperViews[a].measure(MeasureSpec.makeMeasureSpec(a == spanCount - 1 ? availableWidth : itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            availableWidth -= itemWidth;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (spanCount == 1) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        int l = AndroidUtilities.dp(14);
        int t = isTop ? AndroidUtilities.dp(14) : 0;
        for (int a = 0; a < spanCount; a++) {
            int w = wallpaperViews[a].getMeasuredWidth();
            wallpaperViews[a].layout(l, t, l + w, t + wallpaperViews[a].getMeasuredHeight());
            l += w + AndroidUtilities.dp(6);
        }
    }

    public void setParams(int columns, boolean top, boolean bottom) {
        spanCount = columns;
        isTop = top;
        isBottom = bottom;
        for (int a = 0; a < wallpaperViews.length; a++) {
            wallpaperViews[a].setVisibility(a < columns ? VISIBLE : GONE);
            wallpaperViews[a].clearAnimation();
        }
    }

    public void setWallpaper(int type, int index, Object wallpaper, Object selectedWallpaper, Drawable themedWallpaper, boolean themed) {
        currentType = type;
        if (wallpaper == null) {
            wallpaperViews[index].setVisibility(GONE);
            wallpaperViews[index].clearAnimation();
        } else {
            wallpaperViews[index].setVisibility(VISIBLE);
            wallpaperViews[index].setWallpaper(wallpaper, selectedWallpaper, themedWallpaper, themed);
        }
    }

    public void setChecked(int index, final boolean checked, final boolean animated) {
        wallpaperViews[index].setChecked(checked, animated);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (int a = 0; a < spanCount; a++) {
            wallpaperViews[a].invalidate();
        }
    }

    public void setSize(int itemSize) {
        if (size != itemSize) {
            this.size = itemSize;
            requestLayout();
        }
    }
}

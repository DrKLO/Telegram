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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
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
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.WallpapersListActivity;

public class WallpaperCell extends FrameLayout {

    private class WallpaperView extends FrameLayout {

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
                    if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
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

        public void setWallpaper(Object object, long selectedBackground, Drawable themedWallpaper, boolean themed) {
            currentWallpaper = object;
            if (object == null) {
                imageView.setVisibility(INVISIBLE);
                imageView2.setVisibility(VISIBLE);
                if (themed) {
                    imageView2.setImageDrawable(themedWallpaper);
                    imageView2.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    imageView2.setBackgroundColor(selectedBackground == -1 || selectedBackground == Theme.DEFAULT_BACKGROUND_ID ? 0x5a475866 : 0x5a000000);
                    imageView2.setScaleType(ImageView.ScaleType.CENTER);
                    imageView2.setImageResource(R.drawable.ic_gallery_background);
                }
            } else {
                imageView.setVisibility(VISIBLE);
                imageView2.setVisibility(INVISIBLE);
                imageView.setBackgroundDrawable(null);
                imageView.getImageReceiver().setColorFilter(null);
                imageView.getImageReceiver().setAlpha(1.0f);
                if (object instanceof TLRPC.TL_wallPaper) {
                    TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
                    isSelected = wallPaper.id == selectedBackground;
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 100);
                    TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 320);
                    if (image == thumb) {
                        image = null;
                    }
                    int size = image != null ? image.size : wallPaper.document.size;
                    if (wallPaper.pattern) {
                        imageView.setBackgroundColor(0xff000000 | wallPaper.settings.background_color);
                        imageView.setImage(ImageLocation.getForDocument(image, wallPaper.document), "100_100", ImageLocation.getForDocument(thumb, wallPaper.document), null, "jpg", size, 1, wallPaper);
                        imageView.getImageReceiver().setColorFilter(new PorterDuffColorFilter(AndroidUtilities.getPatternColor(wallPaper.settings.background_color), PorterDuff.Mode.SRC_IN));
                        imageView.getImageReceiver().setAlpha(wallPaper.settings.intensity / 100.0f);
                    } else {
                        /*if (wallPaper.settings != null && wallPaper.settings.blur) {
                            imageView.setImage(null, "100_100", thumb, "100_100_b", "jpg", size, 1, wallPaper);
                        } else {*/
                        if (image != null) {
                            imageView.setImage(ImageLocation.getForDocument(image, wallPaper.document), "100_100", ImageLocation.getForDocument(thumb, wallPaper.document), "100_100_b", "jpg", size, 1, wallPaper);
                        } else {
                            imageView.setImage(ImageLocation.getForDocument(wallPaper.document), "100_100", ImageLocation.getForDocument(thumb, wallPaper.document), "100_100_b", "jpg", size, 1, wallPaper);
                        }
                        //}
                    }
                } else if (object instanceof WallpapersListActivity.ColorWallpaper) {
                    WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) object;
                    if (wallPaper.path != null) {
                        imageView.setImage(wallPaper.path.getAbsolutePath(), "100_100", null);
                    } else {
                        imageView.setImageBitmap(null);
                        imageView.setBackgroundColor(0xff000000 | wallPaper.color);
                    }
                    isSelected = wallPaper.id == selectedBackground;
                } else if (object instanceof WallpapersListActivity.FileWallpaper) {
                    WallpapersListActivity.FileWallpaper wallPaper = (WallpapersListActivity.FileWallpaper) object;
                    isSelected = wallPaper.id == selectedBackground;
                    if (wallPaper.originalPath != null) {
                        imageView.setImage(wallPaper.originalPath.getAbsolutePath(), "100_100", null);
                    } else if (wallPaper.path != null) {
                        imageView.setImage(wallPaper.path.getAbsolutePath(), "100_100", null);
                    } else if (wallPaper.resId == Theme.THEME_BACKGROUND_ID) {
                        imageView.setImageDrawable(Theme.getThemedWallpaper(true));
                    } else {
                        imageView.setImageResource(wallPaper.thumbResId);
                    }
                } else if (object instanceof MediaController.SearchImage) {
                    MediaController.SearchImage wallPaper = (MediaController.SearchImage) object;
                    if (wallPaper.photo != null) {
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, 100);
                        TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, 320);
                        if (image == thumb) {
                            image = null;
                        }
                        int size = image != null ? image.size : 0;
                        imageView.setImage(ImageLocation.getForPhoto(image, wallPaper.photo), "100_100", ImageLocation.getForPhoto(thumb, wallPaper.photo), "100_100_b", "jpg", size, 1, wallPaper);
                    } else {
                        imageView.setImage(wallPaper.thumbUrl, "100_100", null);
                    }
                } else {
                    isSelected = false;
                }
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
            if (checkBox.isChecked() || !imageView.getImageReceiver().hasBitmapImage() || imageView.getImageReceiver().getCurrentAlpha() != 1.0f) {
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
        super(context);

        wallpaperViews = new WallpaperView[5];
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

    public void setWallpaper(int type, int index, Object wallpaper, long selectedBackground, Drawable themedWallpaper, boolean themed) {
        currentType = type;
        if (wallpaper == null) {
            wallpaperViews[index].setVisibility(GONE);
            wallpaperViews[index].clearAnimation();
        } else {
            wallpaperViews[index].setVisibility(VISIBLE);
            wallpaperViews[index].setWallpaper(wallpaper, selectedBackground, themedWallpaper, themed);
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
}

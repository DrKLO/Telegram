/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.ActionBar.ActionBar;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ImageReceiver;

public class PhotoViewer {
    private FrameLayout containerView;
    private ClippingImageView animatingImageView;
    private ColorDrawable backgroundDrawable = new ColorDrawable(0xff000000);
    private View parentView;
    private int imageX, imageY;
    private ImageReceiver currentImageReceiver;
    private boolean animationInProgress = false;
    private ActionBar actionBar;
    private ActionBarLayer actionBarLayer;
    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private boolean isVisible;
    private boolean isActionBarVisible = true;

    public static class FrameLayoutTest extends FrameLayout {
        public FrameLayoutTest(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return getInstance().onTouchEvent(event);
        }
    }

    public static class ClippingImageView extends View {
        private int clipBottom;
        private int clipLeft;
        private int clipRight;
        private int clipTop;
        private Rect drawRect;
        private Paint paint;
        private Bitmap bmp;
        public onDrawListener drawListener;

        public static interface onDrawListener {
            public abstract void onDraw();
        }

        public ClippingImageView(Context paramContext) {
            super(paramContext);
            paint = new Paint();
            paint.setFilterBitmap(true);
            drawRect = new Rect();
        }

        public int getClipBottom() {
            return clipBottom;
        }

        public int getClipHorizontal() {
            return clipRight;
        }

        public int getClipLeft() {
            return clipLeft;
        }

        public int getClipRight() {
            return clipRight;
        }

        public int getClipTop() {
            return clipTop;
        }

        public void onDraw(Canvas canvas) {
            if (drawListener != null) {
                drawListener.onDraw();
            }
            if (bmp != null) {
                canvas.save();
                canvas.clipRect(clipLeft / getScaleY(), clipTop / getScaleY(), getWidth() - clipRight / getScaleY(), getHeight() - clipBottom / getScaleY());
                drawRect.set(0, 0, getWidth(), getHeight());
                canvas.drawBitmap(this.bmp, null, drawRect, this.paint);
                canvas.restore();
            }
        }

        public void setClipBottom(int value) {
            clipBottom = value;
            invalidate();
        }

        public void setClipHorizontal(int value) {
            clipRight = value;
            clipLeft = value;
            invalidate();
        }

        public void setClipLeft(int value) {
            clipLeft = value;
            invalidate();
        }

        public void setClipRight(int value) {
            clipRight = value;
            invalidate();
        }

        public void setClipTop(int value) {
            clipTop = value;
            invalidate();
        }

        public void setClipVertical(int value) {
            clipBottom = value;
            clipTop = value;
            invalidate();
        }

        public void setImageBitmap(Bitmap bitmap) {
            bmp = bitmap;
            invalidate();
        }
    }

    private static volatile PhotoViewer Instance = null;
    public static PhotoViewer getInstance() {
        PhotoViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (PhotoViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new PhotoViewer();
                }
            }
        }
        return localInstance;
    }

    public void setParentActivity(Activity activity) {
        parentActivity = activity;

        containerView = new FrameLayoutTest(activity);
        containerView.setFocusable(false);
        containerView.setBackground(backgroundDrawable);

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        animatingImageView = new ClippingImageView(containerView.getContext());
        containerView.addView(animatingImageView);

        actionBar = new ActionBar(activity);
        containerView.addView(actionBar);
        actionBar.setBackgroundColor(0xdd000000);
        actionBar.setItemsBackground(R.drawable.bar_selector_white);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)actionBar.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        actionBar.setLayoutParams(layoutParams);
        actionBarLayer = actionBar.createLayer();
        actionBarLayer.setDisplayHomeAsUpEnabled(true);
        actionBarLayer.setTitle("Photo");
        actionBar.setCurrentActionBarLayer(actionBarLayer);

        actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    closePhoto();
                }
            }
        });

        ActionBarMenu menu = actionBarLayer.createMenu();
        menu.addItem(0, R.drawable.ic_ab_other_white);
    }

    public void openPhoto(final ImageReceiver imageReceiver, final int x, final int y, final View parent) {
        if (parentActivity == null || isVisible) {
            return;
        }
        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        wm.addView(containerView, windowLayoutParams);

        if(android.os.Build.VERSION.SDK_INT >= 11) {
            if (animationInProgress) {
                return;
            }
            isVisible = true;
            animationInProgress = true;
            toggleActionBar(true, false);
            parentView = parent;
            imageX = x;
            imageY = y;

            currentImageReceiver = imageReceiver;
            animatingImageView.setVisibility(View.VISIBLE);
            containerView.invalidate();
            animatingImageView.invalidate();
            animatingImageView.setImageBitmap(imageReceiver.getBitmap());

            containerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    containerView.getViewTreeObserver().removeOnPreDrawListener(this);
                    animatingImageView.drawListener = new ClippingImageView.onDrawListener() {
                        @Override
                        public void onDraw() {
                            animatingImageView.drawListener = null;
                            animatingImageView.post(new Runnable() {
                                @Override
                                public void run() {
                                    imageReceiver.setVisible(false);
                                }
                            });
                        }
                    };

                    animatingImageView.setPivotX(0);
                    animatingImageView.setPivotY(0);
                    animatingImageView.setScaleX(1);
                    animatingImageView.setScaleY(1);
                    animatingImageView.setTranslationX(x + imageReceiver.drawRegion.left);
                    animatingImageView.setTranslationY(y + imageReceiver.drawRegion.top);
                    ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
                    layoutParams.width = imageReceiver.drawRegion.right - imageReceiver.drawRegion.left;
                    layoutParams.height = imageReceiver.drawRegion.bottom - imageReceiver.drawRegion.top;
                    animatingImageView.setLayoutParams(layoutParams);


                    float scaleX = (float)Utilities.displaySize.x / layoutParams.width;
                    float scaleY = (float)(Utilities.displaySize.y - Utilities.statusBarHeight) / layoutParams.height;
                    float scale = scaleX > scaleY ? scaleY : scaleX;
                    float width = layoutParams.width * scale;
                    float height = layoutParams.height * scale;
                    float xPos = (Utilities.displaySize.x - width) / 2.0f;
                    float yPos = (Utilities.displaySize.y - Utilities.statusBarHeight - height) / 2.0f;
                    int clipHorizontal = Math.abs(imageReceiver.drawRegion.left - imageReceiver.imageX);

                    int coords2[] = new int[2];
                    parent.getLocationInWindow(coords2);
                    int clipTop = coords2[1] - Utilities.statusBarHeight - (y + imageReceiver.drawRegion.top);
                    if (clipTop < 0) {
                        clipTop = 0;
                    }
                    int clipBottom = (y + imageReceiver.drawRegion.top + layoutParams.height) - (coords2[1] + parent.getHeight() - Utilities.statusBarHeight);
                    if (clipBottom < 0) {
                        clipBottom = 0;
                    }

                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(animatingImageView, "scaleX", scale),
                            ObjectAnimator.ofFloat(animatingImageView, "scaleY", scale),
                            ObjectAnimator.ofFloat(animatingImageView, "translationX", xPos),
                            ObjectAnimator.ofFloat(animatingImageView, "translationY", yPos),
                            ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0, 255),
                            ObjectAnimator.ofInt(animatingImageView, "clipHorizontal", clipHorizontal, 0),
                            ObjectAnimator.ofInt(animatingImageView, "clipTop", clipTop, 0),
                            ObjectAnimator.ofInt(animatingImageView, "clipBottom", clipBottom, 0),
                            ObjectAnimator.ofFloat(actionBar, "alpha", 0.0f, 1.0f)
                    );

                    animatorSet.setDuration(250);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            animationInProgress = false;
                        }
                    });
                    animatorSet.start();
                    return true;
                }
            });
        }
    }

    public void closePhoto() {
        if (parentActivity == null || !isVisible) {
            return;
        }
        if(android.os.Build.VERSION.SDK_INT >= 11) {
            if (animationInProgress) {
                return;
            }
            isVisible = false;
            animationInProgress = true;
            isActionBarVisible = false;
            animatingImageView.setVisibility(View.VISIBLE);

            int clipHorizontal = Math.abs(currentImageReceiver.drawRegion.left - currentImageReceiver.imageX);

            int coords2[] = new int[2];
            parentView.getLocationInWindow(coords2);
            int clipTop = coords2[1] - Utilities.statusBarHeight - (imageY + currentImageReceiver.drawRegion.top);
            if (clipTop < 0) {
                clipTop = 0;
            }
            int clipBottom = (imageY + currentImageReceiver.drawRegion.top + (currentImageReceiver.drawRegion.bottom - currentImageReceiver.drawRegion.top)) - (coords2[1] + parentView.getHeight() - Utilities.statusBarHeight);
            if (clipBottom < 0) {
                clipBottom = 0;
            }

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(animatingImageView, "scaleX", 1),
                    ObjectAnimator.ofFloat(animatingImageView, "scaleY", 1),
                    ObjectAnimator.ofFloat(animatingImageView, "translationX", imageX + currentImageReceiver.drawRegion.left),
                    ObjectAnimator.ofFloat(animatingImageView, "translationY", imageY + currentImageReceiver.drawRegion.top),
                    ObjectAnimator.ofInt(backgroundDrawable, "alpha", 0),
                    ObjectAnimator.ofInt(animatingImageView, "clipHorizontal", clipHorizontal),
                    ObjectAnimator.ofInt(animatingImageView, "clipTop", clipTop),
                    ObjectAnimator.ofInt(animatingImageView, "clipBottom", clipBottom),
                    ObjectAnimator.ofFloat(actionBar, "alpha", 0.0f)
            );

            animatorSet.setDuration(250);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    containerView.post(new Runnable() {
                        @Override
                        public void run() {
                            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                            wm.removeView(containerView);
                        }
                    });
                    animationInProgress = false;
                    currentImageReceiver.setVisible(true);
                }
            });
            animatorSet.start();
        } else {
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(containerView);
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    private boolean maybeStartDragDown = false;
    private boolean draggingDown = false;
    private int startDraggingPointerIndex = -1;
    private float dragY;
    private float startDragOriginY = 0;

    public boolean onTouchEvent(MotionEvent ev) {
        if (animationInProgress) {
            return false;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (!maybeStartDragDown) {
                maybeStartDragDown = true;
                dragY = ev.getY();
            }
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (!draggingDown && maybeStartDragDown) {
                if (Math.abs(ev.getY() - dragY) >= Utilities.dp(10)) {
                    draggingDown = true;
                    maybeStartDragDown = false;
                    dragY = ev.getY();
                    startDragOriginY = animatingImageView.getTranslationY();
                    if (isActionBarVisible) {
                        toggleActionBar(false, true);
                    }
                    return true;
                }
            } else if (draggingDown) {
                float diff = ev.getY() - dragY;
                float maxValue = Utilities.displaySize.y / 4.0f;
                backgroundDrawable.setAlpha((int)Math.max(127, 255 * (1.0f - (Math.min(Math.abs(diff), maxValue) / maxValue))));
                animatingImageView.setTranslationY(startDragOriginY + diff);
            }
        } else if (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP) {
            if (draggingDown) {
                if (Math.abs(dragY - ev.getY()) > Utilities.displaySize.y / 6.0f) {
                    closePhoto();
                } else {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(animatingImageView, "translationY", startDragOriginY),
                            ObjectAnimator.ofInt(backgroundDrawable, "alpha", 255)
                    );

                    animatorSet.setDuration(250);
                    animatorSet.start();
                }
                draggingDown = false;
            } else {
                toggleActionBar(!isActionBarVisible, true);
            }
        }
        return false;
    }

    private void toggleActionBar(boolean show, boolean animated) {
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
        }
        isActionBarVisible = show;
        actionBar.setEnabled(show);
        actionBarLayer.setEnabled(show);
        if (animated) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(actionBar, "alpha", show ? 1.0f : 0.0f)
            );
            if (!show) {
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        actionBar.setVisibility(View.GONE);
                    }
                });
            }

            animatorSet.setDuration(250);
            animatorSet.start();
        } else {
            actionBar.setAlpha(show ? 1.0f : 0.0f);
            if (!show) {
                actionBar.setVisibility(View.GONE);
            }
        }
    }
}

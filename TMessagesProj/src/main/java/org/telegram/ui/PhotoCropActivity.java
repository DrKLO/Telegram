/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

public class PhotoCropActivity extends BaseFragment {

    public interface PhotoEditActivityDelegate {
        void didFinishEdit(Bitmap bitmap);
    }

    private class PhotoCropView extends FrameLayout {

        Paint rectPaint = null;
        Paint circlePaint = null;
        Paint halfPaint = null;
        float rectSizeX = 600;
        float rectSizeY = 600;
        float rectX = -1, rectY = -1;
        int draggingState = 0;
        float oldX = 0, oldY = 0;
        int bitmapWidth, bitmapHeight, bitmapX, bitmapY;
        int viewWidth, viewHeight;
        boolean freeform;

        public PhotoCropView(Context context) {
            super(context);
            init();
        }

        private void init() {
            rectPaint = new Paint();
            rectPaint.setColor(0x3ffafafa);
            rectPaint.setStrokeWidth(AndroidUtilities.dp(2));
            rectPaint.setStyle(Paint.Style.STROKE);
            circlePaint = new Paint();
            circlePaint.setColor(0xffffffff);
            halfPaint = new Paint();
            halfPaint.setColor(0xc8000000);
            setBackgroundColor(0xff333333);

            setOnTouchListener((view, motionEvent) -> {
                float x = motionEvent.getX();
                float y = motionEvent.getY();
                int cornerSide = AndroidUtilities.dp(14);
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (rectX - cornerSide < x && rectX + cornerSide > x && rectY - cornerSide < y && rectY + cornerSide > y) {
                        draggingState = 1;
                    } else if (rectX - cornerSide + rectSizeX < x && rectX + cornerSide + rectSizeX > x && rectY - cornerSide < y && rectY + cornerSide > y) {
                        draggingState = 2;
                    } else if (rectX - cornerSide < x && rectX + cornerSide > x && rectY - cornerSide + rectSizeY < y && rectY + cornerSide + rectSizeY > y) {
                        draggingState = 3;
                    } else if (rectX - cornerSide + rectSizeX < x && rectX + cornerSide + rectSizeX > x && rectY - cornerSide + rectSizeY < y && rectY + cornerSide + rectSizeY > y) {
                        draggingState = 4;
                    } else if (rectX < x && rectX + rectSizeX > x && rectY < y && rectY + rectSizeY > y) {
                        draggingState = 5;
                    } else {
                        draggingState = 0;
                    }
                    if (draggingState != 0) {
                        PhotoCropView.this.requestDisallowInterceptTouchEvent(true);
                    }
                    oldX = x;
                    oldY = y;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    draggingState = 0;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && draggingState != 0) {
                    float diffX = x - oldX;
                    float diffY = y - oldY;
                    if (draggingState == 5) {
                        rectX += diffX;
                        rectY += diffY;

                        if (rectX < bitmapX) {
                            rectX = bitmapX;
                        } else if (rectX + rectSizeX > bitmapX + bitmapWidth) {
                            rectX = bitmapX + bitmapWidth - rectSizeX;
                        }
                        if (rectY < bitmapY) {
                            rectY = bitmapY;
                        } else if (rectY + rectSizeY > bitmapY + bitmapHeight) {
                            rectY = bitmapY + bitmapHeight - rectSizeY;
                        }
                    } else {
                        if (draggingState == 1) {
                            if (rectSizeX - diffX < 160) {
                                diffX = rectSizeX - 160;
                            }
                            if (rectX + diffX < bitmapX) {
                                diffX = bitmapX - rectX;
                            }
                            if (!freeform) {
                                if (rectY + diffX < bitmapY) {
                                    diffX = bitmapY - rectY;
                                }
                                rectX += diffX;
                                rectY += diffX;
                                rectSizeX -= diffX;
                                rectSizeY -= diffX;
                            } else {
                                if (rectSizeY - diffY < 160) {
                                    diffY = rectSizeY - 160;
                                }
                                if (rectY + diffY < bitmapY) {
                                    diffY = bitmapY - rectY;
                                }
                                rectX += diffX;
                                rectY += diffY;
                                rectSizeX -= diffX;
                                rectSizeY -= diffY;
                            }
                        } else if (draggingState == 2) {
                            if (rectSizeX + diffX < 160) {
                                diffX = -(rectSizeX - 160);
                            }
                            if (rectX + rectSizeX + diffX > bitmapX + bitmapWidth) {
                                diffX = bitmapX + bitmapWidth - rectX - rectSizeX;
                            }
                            if (!freeform) {
                                if (rectY - diffX < bitmapY) {
                                    diffX = rectY - bitmapY;
                                }
                                rectY -= diffX;
                                rectSizeX += diffX;
                                rectSizeY += diffX;
                            } else {
                                if (rectSizeY - diffY < 160) {
                                    diffY = rectSizeY - 160;
                                }
                                if (rectY + diffY < bitmapY) {
                                    diffY = bitmapY - rectY;
                                }
                                rectY += diffY;
                                rectSizeX += diffX;
                                rectSizeY -= diffY;
                            }
                        } else if (draggingState == 3) {
                            if (rectSizeX - diffX < 160) {
                                diffX = rectSizeX - 160;
                            }
                            if (rectX + diffX < bitmapX) {
                                diffX = bitmapX - rectX;
                            }
                            if (!freeform) {
                                if (rectY + rectSizeX - diffX > bitmapY + bitmapHeight) {
                                    diffX = rectY + rectSizeX - bitmapY - bitmapHeight;
                                }
                                rectX += diffX;
                                rectSizeX -= diffX;
                                rectSizeY -= diffX;
                            } else {
                                if (rectY + rectSizeY + diffY > bitmapY + bitmapHeight) {
                                    diffY = bitmapY + bitmapHeight - rectY - rectSizeY;
                                }
                                rectX += diffX;
                                rectSizeX -= diffX;
                                rectSizeY += diffY;
                                if (rectSizeY < 160) {
                                    rectSizeY = 160;
                                }
                            }
                        } else if (draggingState == 4) {
                            if (rectX + rectSizeX + diffX > bitmapX + bitmapWidth) {
                                diffX = bitmapX + bitmapWidth - rectX - rectSizeX;
                            }
                            if (!freeform) {
                                if (rectY + rectSizeX + diffX > bitmapY + bitmapHeight) {
                                    diffX = bitmapY + bitmapHeight - rectY - rectSizeX;
                                }
                                rectSizeX += diffX;
                                rectSizeY += diffX;
                            } else {
                                if (rectY + rectSizeY + diffY > bitmapY + bitmapHeight) {
                                    diffY = bitmapY + bitmapHeight - rectY - rectSizeY;
                                }
                                rectSizeX += diffX;
                                rectSizeY += diffY;
                            }
                            if (rectSizeX < 160) {
                                rectSizeX = 160;
                            }
                            if (rectSizeY < 160) {
                                rectSizeY = 160;
                            }
                        }
                    }

                    oldX = x;
                    oldY = y;
                    invalidate();
                }
                return true;
            });
        }

        private void updateBitmapSize() {
            if (viewWidth == 0 || viewHeight == 0 || imageToCrop == null) {
                return;
            }
            float percX = (rectX - bitmapX) / bitmapWidth;
            float percY = (rectY - bitmapY) / bitmapHeight;
            float percSizeX = rectSizeX / bitmapWidth;
            float percSizeY = rectSizeY / bitmapHeight;
            float w = imageToCrop.getWidth();
            float h = imageToCrop.getHeight();
            float scaleX = viewWidth / w;
            float scaleY = viewHeight / h;
            if (scaleX > scaleY) {
                bitmapHeight = viewHeight;
                bitmapWidth = (int) Math.ceil(w * scaleY);
            } else {
                bitmapWidth = viewWidth;
                bitmapHeight = (int) Math.ceil(h * scaleX);
            }
            bitmapX = (viewWidth - bitmapWidth) / 2 + AndroidUtilities.dp(14);
            bitmapY = (viewHeight - bitmapHeight) / 2 + AndroidUtilities.dp(14);

            if (rectX == -1 && rectY == -1) {
                if (freeform) {
                    rectY = bitmapY;
                    rectX = bitmapX;
                    rectSizeX = bitmapWidth;
                    rectSizeY = bitmapHeight;
                } else {
                    if (bitmapWidth > bitmapHeight) {
                        rectY = bitmapY;
                        rectX = (viewWidth - bitmapHeight) / 2 + AndroidUtilities.dp(14);
                        rectSizeX = bitmapHeight;
                        rectSizeY = bitmapHeight;
                    } else {
                        rectX = bitmapX;
                        rectY = (viewHeight - bitmapWidth) / 2 + AndroidUtilities.dp(14);
                        rectSizeX = bitmapWidth;
                        rectSizeY = bitmapWidth;
                    }
                }
            } else {
                rectX = percX * bitmapWidth + bitmapX;
                rectY = percY * bitmapHeight + bitmapY;
                rectSizeX = percSizeX * bitmapWidth;
                rectSizeY = percSizeY * bitmapHeight;
            }
            invalidate();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            viewWidth = right - left - AndroidUtilities.dp(28);
            viewHeight = bottom - top - AndroidUtilities.dp(28);
            updateBitmapSize();
        }

        public Bitmap getBitmap() {
            float percX = (rectX - bitmapX) / bitmapWidth;
            float percY = (rectY - bitmapY) / bitmapHeight;
            float percSizeX = rectSizeX / bitmapWidth;
            float percSizeY = rectSizeY / bitmapWidth;
            int x = (int) (percX * imageToCrop.getWidth());
            int y = (int) (percY * imageToCrop.getHeight());
            int sizeX = (int) (percSizeX * imageToCrop.getWidth());
            int sizeY = (int) (percSizeY * imageToCrop.getWidth());
            if (x < 0) {
                x = 0;
            }
            if (y < 0) {
                y = 0;
            }
            if (x + sizeX > imageToCrop.getWidth()) {
                sizeX = imageToCrop.getWidth() - x;
            }
            if (y + sizeY > imageToCrop.getHeight()) {
                sizeY = imageToCrop.getHeight() - y;
            }
            try {
                return Bitmaps.createBitmap(imageToCrop, x, y, sizeX, sizeY);
            } catch (Throwable e) {
                FileLog.e(e);
                System.gc();
                try {
                    return Bitmaps.createBitmap(imageToCrop, x, y, sizeX, sizeY);
                } catch (Throwable e2) {
                    FileLog.e(e2);
                }
            }
            return null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawable != null) {
                try {
                    drawable.setBounds(bitmapX, bitmapY, bitmapX + bitmapWidth, bitmapY + bitmapHeight);
                    drawable.draw(canvas);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            canvas.drawRect(bitmapX, bitmapY, bitmapX + bitmapWidth, rectY, halfPaint);
            canvas.drawRect(bitmapX, rectY, rectX, rectY + rectSizeY, halfPaint);
            canvas.drawRect(rectX + rectSizeX, rectY, bitmapX + bitmapWidth, rectY + rectSizeY, halfPaint);
            canvas.drawRect(bitmapX, rectY + rectSizeY, bitmapX + bitmapWidth, bitmapY + bitmapHeight, halfPaint);

            canvas.drawRect(rectX, rectY, rectX + rectSizeX, rectY + rectSizeY, rectPaint);

            int side = AndroidUtilities.dp(1);
            canvas.drawRect(rectX + side, rectY + side, rectX + side + AndroidUtilities.dp(20), rectY + side * 3, circlePaint);
            canvas.drawRect(rectX + side, rectY + side, rectX + side * 3, rectY + side + AndroidUtilities.dp(20), circlePaint);

            canvas.drawRect(rectX + rectSizeX - side - AndroidUtilities.dp(20), rectY + side, rectX + rectSizeX - side, rectY + side * 3, circlePaint);
            canvas.drawRect(rectX + rectSizeX - side * 3, rectY + side, rectX + rectSizeX - side, rectY + side + AndroidUtilities.dp(20), circlePaint);

            canvas.drawRect(rectX + side, rectY + rectSizeY - side - AndroidUtilities.dp(20), rectX + side * 3, rectY + rectSizeY - side, circlePaint);
            canvas.drawRect(rectX + side, rectY + rectSizeY - side * 3, rectX + side + AndroidUtilities.dp(20), rectY + rectSizeY - side, circlePaint);

            canvas.drawRect(rectX + rectSizeX - side - AndroidUtilities.dp(20), rectY + rectSizeY - side * 3, rectX + rectSizeX - side, rectY + rectSizeY - side, circlePaint);
            canvas.drawRect(rectX + rectSizeX - side * 3, rectY + rectSizeY - side - AndroidUtilities.dp(20), rectX + rectSizeX - side, rectY + rectSizeY - side, circlePaint);

            for (int a = 1; a < 3; a++) {
                canvas.drawRect(rectX + rectSizeX / 3 * a, rectY + side, rectX + side + rectSizeX / 3 * a, rectY + rectSizeY - side, circlePaint);
                canvas.drawRect(rectX + side, rectY + rectSizeY / 3 * a, rectX - side + rectSizeX, rectY + rectSizeY / 3 * a + side, circlePaint);
            }
        }
    }

    private Bitmap imageToCrop;
    private BitmapDrawable drawable;
    private PhotoEditActivityDelegate delegate = null;
    private PhotoCropView view;
    private boolean sameBitmap = false;
    private boolean doneButtonPressed = false;
    private String bitmapKey;

    private final static int done_button = 1;

    public PhotoCropActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        if (imageToCrop == null) {
            String photoPath = getArguments().getString("photoPath");
            Uri photoUri = getArguments().getParcelable("photoUri");
            if (photoPath == null && photoUri == null) {
                return false;
            }
            if (photoPath != null) {
                File f = new File(photoPath);
                if (!f.exists()) {
                    return false;
                }
            }
            int size;
            if (AndroidUtilities.isTablet()) {
                size = AndroidUtilities.dp(520);
            } else {
                size = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            }
            imageToCrop = ImageLoader.loadBitmap(photoPath, photoUri, size, size, true);
            if (imageToCrop == null) {
                return false;
            }
        }
        drawable = new BitmapDrawable(imageToCrop);
        super.onFragmentCreate();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (bitmapKey != null) {
            if (ImageLoader.getInstance().decrementUseCount(bitmapKey) && !ImageLoader.getInstance().isInMemCache(bitmapKey, false)) {
                bitmapKey = null;
            }
        }
        if (bitmapKey == null && imageToCrop != null && !sameBitmap) {
            imageToCrop.recycle();
            imageToCrop = null;
        }
        drawable = null;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.ACTION_BAR_MEDIA_PICKER_COLOR);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, false);
        actionBar.setTitleColor(0xffffffff);
        actionBar.setItemsColor(0xffffffff, false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("CropImage", R.string.CropImage));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (delegate != null && !doneButtonPressed) {
                        Bitmap bitmap = view.getBitmap();
                        if (bitmap == imageToCrop) {
                            sameBitmap = true;
                        }
                        delegate.didFinishEdit(bitmap);
                        doneButtonPressed = true;
                    }
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        fragmentView = view = new PhotoCropView(context);
        ((PhotoCropView) fragmentView).freeform = getArguments().getBoolean("freeform", false);
        fragmentView.setLayoutParams(new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    public void setDelegate(PhotoEditActivityDelegate delegate) {
        this.delegate = delegate;
    }
}
